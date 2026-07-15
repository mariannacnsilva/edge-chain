# Arquitetura Dualchain — EdgechainRegulator (Sidechain) + EdgechainMain (Mainchain)

Este documento descreve a nova arquitetura **dualchain** implementada a partir do
contrato singlechain `EdgeChain`, substituindo o contrato `Bridge` como camada de
comunicação entre as chains.

---

## 1. Visão geral

Na versão **singlechain**, o contrato `EdgeChain` concentrava todas as
responsabilidades: cadastro de dispositivos, controle de comportamento,
penalização, pagamento e a própria lógica de negócio.

Na versão **dualchain**, as responsabilidades são separadas em dois contratos:

| Camada | Contrato | Responsabilidade |
|--------|----------|------------------|
| **Sidechain** | `EdgechainRegulator` | Controle, validação, reputação, penalidade, aprovação/rejeição e encaminhamento |
| **Mainchain** | `EdgechainMain` | Execução definitiva da operação aprovada e verificação de temperatura crítica |

### Fluxo da comunicação

```
Sensor IoT
    |
    v
EdgechainRegulator (SIDECHAIN)
    |-- valida dispositivo (cadastro automático)
    |-- analisa comportamento (excesso de requisições?)
    |-- aplica penalidade / reputação / bloqueio
    |-- aprova ou rejeita
    v
EdgechainMain (MAINCHAIN)
    |-- executa operação definitiva
    |-- verifica temperatura crítica (> 80)
    |-- retorna (temperatureCritical, deviceShouldShutdown, gasUsed)
    v
EdgechainRegulator (SIDECHAIN)
    |-- atualiza custo (accumulatedGas)
    |-- atualiza recompensa (calculateReward)
    v
Sensor IoT (recebe resultado / ordem de desligamento)
```

---

## 2. Arquivos criados

| Arquivo | Descrição |
|---------|-----------|
| `edge-chain/contracts/EdgechainRegulator.sol` | Contrato **Regulador (sidechain)** |
| `edge-chain/contracts/EdgechainMain.sol` | Contrato **Principal (mainchain)** |
| `edge-chain/migrations/5_dualchain_migrations.js` | Deploy + wiring dos dois contratos |
| `edge-chain/contracts/compile-dualchain.js` | Compila os dois contratos (gera `.abi`/`.bin`) |
| `edge-chain/contracts/generate-wrapper-dualchain.js` | Gera os wrappers Java (web3j) |
| `edge-chain/test/dualchain.test.js` | Testes funcionais + comparação de desempenho |
| `edge-chain/contracts/EdgechainMain.abi` / `.bin` | Artefatos compilados (gerados) |
| `edge-chain/contracts/EdgechainRegulator.abi` / `.bin` | Artefatos compilados (gerados) |
| `edge-chain-client/src/main/java/EdgechainMain.java` | Wrapper Java (web3j) da mainchain |
| `edge-chain-client/src/main/java/EdgechainRegulator.java` | Wrapper Java (web3j) da sidechain |

### Alterações realizadas
- Nenhum contrato existente foi removido — o `EdgeChain` (singlechain) e o
  `Bridge` continuam no projeto para permitir a **comparação**.
- **Cliente Java adaptado** para o fluxo dualchain:
  - `DispositivoIoT.java` — agora envia toda leitura para o **EdgechainRegulator**
    (sidechain) via `registerTemperatureReading(...)`, lê o evento
    `ReadingValidated` para saber se foi aprovada/rejeitada, e para de operar se
    for bloqueado ou receber ordem de desligamento.
  - `GerenciadorDispositivos.java` — deixa de submeter *batches* no `Bridge` e
    passa a atuar como **relayer**: `encaminharParaMainchain(...)` executa
    `EdgechainMain.executeTemperatureOperation(...)` na mainchain, lê o gas e o
    `CriticalAlert`, e reporta o custo de volta à sidechain com
    `updateExecutionCost(...)`.
- O `Bridge` é **substituído na nova arquitetura** pelo par
  `EdgechainRegulator`/`EdgechainMain` (o Bridge apenas ancorava hashes de batch;
  agora o Regulador faz validação/reputação e encaminha operações reais).
- `truffle-config.js` já define as redes `sidechain` (porta 8545) e `mainchain`
  (porta 7545) — reutilizadas sem alteração.

---

## 3. Contrato Regulador (Sidechain) — `EdgechainRegulator`

Ponto de entrada de **todas** as leituras dos sensores.

- **`registerTemperatureReading(deviceId, deviceType, operationType, temperature, timestamp)`**
  — recebe a leitura. O endereço do dispositivo é `msg.sender` (mesmo padrão do `EdgeChain`).
- **Cadastro automático** — cria um `struct Device` na primeira leitura.
- **Registro de comportamento** — atualiza `transactionCount`, `unsafeOperations`,
  `accumulatedGas` e guarda `ReadingHistory` (temperatura, timestamp, gasUsed).
- **Avaliação de comportamento** — detecta excesso de requisições:
  intervalo `< 60s` conta como "rajada"; `>= 500` leituras rápidas consecutivas =
  comportamento anormal → `penaltyLevel++`, `unsafeOperations++` e **rejeita**.
  `penaltyLevel > 5` → **bloqueia** o dispositivo (`DeviceBlocked`).
- **Encaminhamento** — se válida, chama `EdgechainMain.executeTemperatureOperation(...)`.
- **Controle de custo** — soma o gas da sidechain + o gas retornado pela mainchain
  em `accumulatedGas`.
- **Recompensa** — `calculateReward(device)` considera gas, reputação e penalidade.

**Eventos:** `DeviceRegistered`, `ReadingValidated`, `DeviceBlocked`, `DeviceUnblocked`, `RewardUpdated`.

#### Detecção de anomalia por TAXA (janela de tempo)

A avaliação de comportamento usa **taxa** (leituras por janela), não uma contagem
de "500 em sequência". Constantes em `EdgechainRegulator.sol`:

| Constante | Valor | Significado |
|-----------|-------|-------------|
| `WINDOW` | `60` s | janela de avaliação |
| `MAX_PER_WINDOW` | `40` | leituras/janela acima disto ⇒ anormal |
| `MAX_PENALTY_LEVEL` | `5` | `penaltyLevel > 5` ⇒ bloqueia |
| `BLOCK_DURATION` | `60` s | duração do bloqueio temporário |

- Cadência honesta (~1/min ou ~30/min) fica **abaixo** de `MAX_PER_WINDOW` ⇒ aceita.
- Uma **rajada** (muitas leituras na mesma janela) ultrapassa o limite ⇒
  `penaltyLevel++`, `unsafeOperations++`, reputação reduzida e **rejeição**.
- `penaltyLevel > 5` ⇒ **bloqueio temporário** (`blocked=true`, `blockedUntil = timestamp + BLOCK_DURATION`).

#### Bloqueio temporário e reabilitação

O bloqueio **não é permanente**. Quando o dispositivo volta a enviar uma leitura
**após** `blockedUntil`, o contrato o **reabilita** (`blocked=false`, penalidade
volta ao limite, janela reiniciada) e emite `DeviceUnblocked`, retomando o
processamento normal. Enquanto dentro do prazo, as leituras são rejeitadas.

No cliente, o `DispositivoIoT` **não encerra** ao ser bloqueado (bloqueio é
temporário): ele registra o estado, segue enviando e volta a operar após a
reabilitação. O dispositivo de índice **4 é "malicioso"**: envia uma rajada de
~50 leituras para acionar a detecção, sendo penalizado e bloqueado
temporariamente (demonstra os passos 5 e 12).

## 4. Contrato Principal (Mainchain) — `EdgechainMain`

Executa **apenas** operações aprovadas pela sidechain.

- **`executeTemperatureOperation(device, operationType, temperature, timestamp)`**
  — só pode ser chamada pelo regulador (`onlyRegulator`).
- Verifica temperatura crítica (`temperature > 80` → `temperatureCritical = true`).
- Sinaliza `deviceShouldShutdown = true` em caso crítico (a **sidechain** informa o sensor).
- Retorna `(temperatureCritical, deviceShouldShutdown, gasUsed)`.

**Eventos:** `TemperatureProcessed`, `CriticalAlert`.

### Modos de operação
- **Integrado (mesma EVM):** Regulador e Main na mesma rede; a chamada
  Regulador→Main é direta (interface `IEdgechainMain`). É o modo usado nos testes.
- **Cross-chain (duas redes):** Main na `mainchain` (7545), Regulador na
  `sidechain` (8545). A chamada on-chain falha (try/catch) e o **relayer Java**
  (`GerenciadorDispositivos`) faz o encaminhamento off-chain, reportando o custo
  de volta via `updateExecutionCost(device, success, mainGasUsed)`.

---

## 5. Como compilar e gerar wrappers

```bash
cd edge-chain/contracts

# 1) Compilar os dois contratos (gera .abi e .bin)
node compile-dualchain.js

# 2) Gerar os wrappers Java (web3j) em com/edge/chain/
node generate-wrapper-dualchain.js
```

Depois copie `EdgechainMain.java` e `EdgechainRegulator.java` para
`edge-chain-client/src/main/java/` (removendo o `package com.edge.chain;`),
seguindo o mesmo procedimento já usado com `Bridge.java` / `EdgeChain.java`.

---

## 6. Como executar os testes

Os testes usam o Ganache embutido do Truffle (não é necessário subir nada):

```bash
cd edge-chain
npx truffle test test/dualchain.test.js
```

Resultado esperado (6 testes passando):
- cadastro automático de dispositivo;
- encaminhamento da leitura válida para a mainchain;
- alerta crítico + ordem de desligamento (temp > 80);
- leituras rápidas não bloqueiam abaixo do limite;
- cálculo de recompensa;
- **coleta de métricas** singlechain × dualchain.

Para rodar contra a rede sidechain real (Ganache na porta 8545):

```bash
npx truffle test test/dualchain.test.js --network sidechain
```

### Deploy nas redes
```bash
# Mainchain (porta 7545): implanta apenas EdgechainMain (migration 4)
npx truffle migrate --network mainchain -f 4 --to 4

# Sidechain (porta 8545): implanta apenas EdgechainRegulator (migration 5),
# SEM configurar mainchainContract (fica address(0)) -> encaminhamento via relayer,
# evitando dupla execucao no modo cross-chain.
npx truffle migrate --network sidechain -f 5 --to 5
```

---

## 7. Como comparar desempenho: singlechain × dualchain

O teste `dualchain.test.js` (bloco *"coleta metricas"*) executa **N operações**
em cada arquitetura e imprime as quatro métricas pedidas:

| Métrica | Como é medida |
|---------|---------------|
| **Gas consumido** | soma de `receipt.gasUsed` de cada transação (total e média/tx) |
| **Latência** | tempo de parede (ms) do bloco de N operações (`Date.now()`) |
| **Transações processadas** | `getStats()` na sidechain (`readings/approved/rejected`) e `getTotalOperations()` na mainchain |
| **TPS** | `N / (latência_em_segundos)` |

Exemplo de saída (N = 20, Ganache local):

```
================ COMPARACAO SINGLECHAIN x DUALCHAIN ================
Operacoes (N)          : 20
SINGLECHAIN  gas total : 1152086  | media/tx: 57604
SINGLECHAIN  latencia  : 2486 ms  | TPS: 8.05
DUALCHAIN    gas total : 3905358  | media/tx: 195268
DUALCHAIN    latencia  : 3748 ms  | TPS: 5.34
Sidechain -> leituras: 20, aprovadas: 20, rejeitadas: 0
Mainchain -> operacoes definitivas: 20
===================================================================
```

> **Interpretação para o TCC:** a dualchain consome mais gas por operação e tem
> TPS menor no modo integrado (há a validação/reputação + a chamada interna à
> mainchain). Em contrapartida, oferece **separação de responsabilidades**,
> **controle de comportamento** e **bloqueio de dispositivos maliciosos** antes de
> tocar a chain definitiva. No modo **cross-chain**, a sidechain absorve o
> volume de leituras e só as operações aprovadas chegam à mainchain, reduzindo a
> carga na chain principal — que é o objetivo da arquitetura edge.

Para ajustar o experimento, altere a constante `N` no início do bloco de métricas.

---

## 8. Executando o cliente Java (cross-chain real)

O cliente Java (`MultiIoTBlockchain`) simula os 5 sensores contra **duas** redes
Ganache reais (sidechain 8545 + mainchain 7545), com o gerenciador atuando como
relayer.

### Passos
1. Suba dois Ganache (portas 8545 e 7545).
2. Faça o deploy:
   ```bash
   cd edge-chain
   npx truffle migrate --network mainchain -f 4 --to 4   # EdgechainMain (7545)
   npx truffle migrate --network sidechain -f 5 --to 5   # EdgechainRegulator (8545)
   ```
3. Em `MultiIoTBlockchain.java`, ajuste os endereços:
   - `sideChainContratoAddr` = endereço do **EdgechainRegulator** (sidechain);
   - `mainChainContratoAddr` = endereço do **EdgechainMain** (mainchain).
4. Compile e rode (Maven):
   ```bash
   cd edge-chain-client
   mvn compile
   mvn exec:java -Dexec.mainClass=MultiIoTBlockchain
   ```

> Se você alterar os contratos `.sol`, regenere os wrappers:
> `node compile-dualchain.js && node generate-wrapper-dualchain.js` e recopie
> `EdgechainMain.java` / `EdgechainRegulator.java` para
> `edge-chain-client/src/main/java/` (sem a linha `package com.edge.chain;`).

### Fluxo no cliente
```
DispositivoIoT.registerTemperatureReading()  ─► EdgechainRegulator (sidechain)
        │ (evento ReadingValidated: approved?)
        ▼
GerenciadorDispositivos.encaminharParaMainchain()
        ├─► EdgechainMain.executeTemperatureOperation()  (mainchain) ─► gas, CriticalAlert
        └─► EdgechainRegulator.updateExecutionCost()     (sidechain) ─► accumulatedGas
```
As métricas do cliente (transações na sidechain, operações encaminhadas à
mainchain e alertas críticos) são impressas por `exibirEstatisticas()`.

---

## 9. Comparação com a singlechain (`SingleIoTBlockchain`)

Para comparar as arquiteturas, o runner `SingleIoTBlockchain` executa os **mesmos
5 dispositivos**, com a **mesma cadência e duração** do `MultiIoTBlockchain`, mas
enviando todas as solicitações ao contrato **único** `EdgeChain` (via
`any_operation`). Ao final imprime as mesmas categorias de métricas — em **fluxo
único**, já que a singlechain tem uma só chain.

### Arquivos
| Arquivo | Papel |
|---------|-------|
| `edge-chain-client/src/main/java/SingleIoTBlockchain.java` | Ponto de entrada singlechain |
| `edge-chain-client/src/main/java/GerenciadorDispositivosSingle.java` | Gerenciador (1 chain, sem relayer) + financiamento do contrato |
| `edge-chain-client/src/main/java/DispositivoIoTSingle.java` | Dispositivo que chama `EdgeChain.any_operation` |
| `edge-chain-client/src/main/java/MetricasExecucao.java` | Reutilizado; ganhou o fluxo `...Singlechain` (aditivo) |

O contrato `EdgeChain` **não foi alterado**. O `EdgeChain.java` (wrapper) também não.

### Passos
1. Suba o Ganache (ex.: 9545) e implante o `EdgeChain` (migration 3, rede `singlechain`):
   ```bash
   cd edge-chain
   npx truffle migrate --network singlechain -f 3 --to 3
   ```
2. Em `SingleIoTBlockchain.java`, ajuste `contratoAddr` (endereço do `EdgeChain`),
   `rpcUrl` e a credencial `funder` (conta com saldo).
3. Compile e rode:
   ```bash
   cd edge-chain-client
   mvn compile
   mvn exec:java -Dexec.mainClass=SingleIoTBlockchain
   ```
4. Rode também o `MultiIoTBlockchain` com a **mesma `duracao`/intervalo** e compare
   os relatórios.

### Observações
- `SingleIoTBlockchain` **financia o contrato** (`financiarContrato`, 1 ETHER)
  antes de iniciar os dispositivos: `any_operation` faz um payout periódico
  (`transfer`) que reverteria sem saldo no contrato. Isso apenas envia ether ao
  `receive()` payable existente — **não altera o contrato**.
- 1 leitura lógica = **1** `any_operation` na singlechain, contra **2 tx**
  (sidechain + mainchain) por leitura na dualchain — essa é a diferença
  arquitetural medida.

### Mapeamento das métricas (single ↔ dual)
| Singlechain | Dualchain (comparável) |
|-------------|------------------------|
| leituras (single) | leituras **sidechain** (entrada de leituras lógicas) |
| gas total (single) | gas total **sidechain + mainchain** |
| gas por dispositivo (single) | gas por dispositivo (side + main) |
| latência (single) | latência sidechain / mainchain |
| TPS (single) | TPS sidechain / mainchain |

A comparação é **baseada em tempo** (mesma `duracao`): os totais são comparáveis
porém não idênticos entre execuções; **latência (ms/op)** e **TPS** são as métricas
normalizadas mais confiáveis para a comparação.
