# REGRAS DO CONTRATO BRIDGE

**Documento Técnico - Sincronização Side Chain ↔ Main Chain**

**Data:** 26 de May de 2026

---

## 1. REGRAS DE SUBMISSÃO DE BATCHES

| Regra | Descrição |
|-------|-----------|
| **State Hash ≠ 0** | Não pode submeter um hash vazio (bytes32(0)) |
| **Block Height > 0** | A altura do bloco deve ser um número positivo |
| **Apenas Validadores** | Apenas endereços com permissão de validador podem submeter batches |
| **Apenas Owner configura** | Apenas o owner pode adicionar/remover validadores |

---

## 2. REGRAS DE VALIDADORES

| Regra | Detalhe |
|-------|---------|
| **Owner é validador padrão** | Na construção, o owner recebe automaticamente permissão |
| **Não duplicar validadores** | Não pode adicionar um endereço que já é validador |
| **Endereço válido** | Não pode usar endereço 0x0 |
| **Não remover owner** | O owner nunca pode ser removido como validador |

---

## 3. REGRAS DE CONSENSO MULTI-VALIDADOR

| Regra | Detalhe |
|-------|---------|
| **Mínimo 2 validadores** | Precisa de acordo de pelo menos 2 validadores |
| **Todos devem ser validadores** | Todos os endereços passados devem estar cadastrados |
| **Sem duplicação** | A mesma validação não usa duplicatas |

---

## 4. REGRAS DE INTEGRIDADE DE DADOS

| Regra | Detalhe |
|-------|---------|
| **Histórico imutável** | Uma vez ancorado, o batch não pode ser alterado |
| **Cada batch tem ID único** | batchCounter incrementa automaticamente |
| **Rastreamento por submitter** | Cada validador tem registro do seu último batch |
| **Timestamp automático** | Usa block.timestamp da blockchain (não manipulável) |

---

## 5. REGRAS DE ESTADO DO CONTRATO

| Variável | Regra |
|----------|-------|
| **batchCounter** | Sempre incrementa após submissão (nunca volta para trás) |
| **ultimoBatchSubmitter** | Mapeia cada validador ao seu último batch |
| **helloWorldMainChain** | Pode ser atualizado apenas pelo owner |

---

## 6. REGRAS DE CONSULTA (View Functions)

| Função | Regra |
|--------|-------|
| **obterBatch()** | Apenas pode consultar batches que existem (ID < batchCounter) |
| **validarBatch()** | Verifica se estado hash e block height correspondem |
| **estaAncorado()** | Verifica se um state hash foi registrado alguma vez |

---

## 7. REGRAS DE EVENTOS (Auditoria)

| Evento | Quando ocorre |
|--------|---------------|
| **BatchAncorado** | Toda vez que um batch é submetido com sucesso |
| **ValidadorAdicionado** | Quando owner adiciona novo validador |
| **ValidadorRemovido** | Quando owner remove um validador |

---

## 8. FLUXO DE VALIDAÇÃO

```
Ordem de Validação para Submissão de Batch:

1. ✓ msg.sender é validador? → Se NÃO: REJEITA
2. ✓ stateHash ≠ 0x0? → Se NÃO: REJEITA
3. ✓ blockHeight > 0? → Se NÃO: REJEITA
4. ✓ ACEITA E REGISTRA:
   • Armazena em mapping
   • Incrementa batchCounter
   • Emite evento BatchAncorado
```

---

## 9. EXEMPLOS DE CENÁRIOS

### ✅ ACEITO:

```solidity
// Validador submete com dados válidos
submeterBatch(
    0xabc123...,  // ≠ 0
    100,          // > 0
    50            // Transações
);
// Resultado: Batch ID 0 criado, evento emitido
```

### ❌ REJEITADO:

```solidity
// Não-validador tenta submeter
submeterBatch(0xabc123..., 100, 50);
// Erro: "Apenas validadores podem submeter batches"

// State hash vazio
submeterBatch(0x0, 100, 50);
// Erro: "State hash não pode ser vazio"

// Block height inválido
submeterBatch(0xabc123..., 0, 50);
// Erro: "Block height deve ser positivo"
```

---

## 10. SEGURANÇA DO CONTRATO

| Aspecto | Como é garantido |
|---------|------------------|
| **Imutabilidade** | Dados armazenados em mapping, não podem ser alterados |
| **Autorização** | Modifiers (apenasOwner, validadorRequerido) |
| **Auditoria** | Eventos registram tudo na blockchain |
| **Consenso** | Função alternativa requer 2+ validadores |
| **Rastreabilidade** | Cada batch armazena quem submeteu e quando |

---

## Conclusão

Estas regras garantem que **apenas entidades autorizadas possam ancorar batches** e que **nada possa ser falsificado após registro**. A imutabilidade e auditoria são garantidas pela natureza da blockchain, criando um sistema de sincronização confiável e resistente a manipulações.

---

**Edge-Chain Project - TCC** | Documento técnico gerado em 26/05/2026
