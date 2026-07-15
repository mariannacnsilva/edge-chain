import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DispositivoIoTSingle implements Runnable {

    // Configurações do Dispositivo
    private int dispositivoId;
    private Web3j web3j;
    private EdgeChain contrato;              // contrato UNICO (singlechain)
    private String contratoAddr;
    private Credentials credenciais;
    private boolean ativo;

    // Configurações de Sensor (identicas ao DispositivoIoT para comparacao justa)
    private double temperaturaBase;
    private double temperaturaAtual;
    private double variacaoMaxima; // ±ºC por leitura
    private Random random;

    // Limiares de Alerta
    private static final double LIMITE_CRITICO_ALTO = 45.0;
    private static final double LIMITE_CRITICO_BAIXO = -10.0;
    private static final double LIMITE_AVISO = 40.0;

    // Codigos de retorno de any_operation (definidos pelo contrato EdgeChain)
    private static final int STATUS_VERSAO_REJEITADA = 2;
    private static final int STATUS_BLOQUEADO = 3;

    // Estatísticas / estado observado
    private int totalLeituras;
    private int totalAlertas;
    private int totalOperacoes;
    private int totalRejeitadas;
    private boolean cadastrado;
    private boolean bloqueado;
    private BigInteger saldoAtual = BigInteger.ZERO;
    private GerenciadorDispositivosSingle gerenciador;

    public DispositivoIoTSingle(int dispositivoId, Web3j web3j, Credentials credenciais, String contratoAddr, GerenciadorDispositivosSingle gerenciador) {
        this.dispositivoId = dispositivoId;
        this.credenciais = credenciais;
        this.web3j = web3j;
        this.contratoAddr = contratoAddr;
        this.contrato = EdgeChain.load(contratoAddr, web3j, credenciais,
            BigInteger.valueOf(20_000_000_000L),
            BigInteger.valueOf(500_000));
        this.gerenciador = gerenciador;

        this.random = new Random();
        this.temperaturaBase = 20.0 + (random.nextDouble() * 10);
        this.temperaturaAtual = this.temperaturaBase;
        this.variacaoMaxima = 0.5;
        this.totalLeituras = 0;
        this.totalAlertas = 0;
        this.totalOperacoes = 0;
        this.totalRejeitadas = 0;
        this.cadastrado = false;
        this.bloqueado = false;
    }

    @Override
    public void run() {
        ativo = true;
        System.out.println("[Dispositivo " + dispositivoId + "]: iniciado (SINGLECHAIN)");

        try {
            int operationCount = 0;

            while (ativo) {
                execucaoCicloDispositivo();
                operationCount++;
                System.out.println("[Dispositivo " + dispositivoId + "]: " + "Operação #" + operationCount + " concluída");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[Dispositivo " + dispositivoId + "]: Dispositivo interrompido");
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        } finally {
            exibirEstatisticas();
        }
    }

    private void exibirEstatisticas() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ESTATÍSTICAS DO DISPOSITIVO " + dispositivoId + " (SINGLECHAIN)");
        System.out.println("=".repeat(60));
        System.out.println("Cadastrado no contrato: " + (cadastrado ? "sim" : "nao"));
        System.out.println("Bloqueado (penalidade alta): " + (bloqueado ? "sim" : "nao"));
        System.out.println("Total de Leituras: " + totalLeituras);
        System.out.println("Total de Alertas: " + totalAlertas);
        System.out.println("Operações na EdgeChain: " + totalOperacoes);
        System.out.println("Operações rejeitadas: " + totalRejeitadas);
        System.out.println("Saldo/recompensa (wei): " + saldoAtual);
        System.out.println("Temperatura Atual: " + String.format("%.2f°C", temperaturaAtual));
        System.out.println("=".repeat(60) + "\n");
    }

    private void execucaoCicloDispositivo() throws Exception {
        // 1. Simula sensor (gera a leitura de temperatura)
        lerTemperatura();

        // 2. Envia a operacao para o contrato UNICO (executa os 6 passos on-chain)
        enviarOperacaoSinglechain();

        // 3. Alertas locais (observabilidade no dispositivo)
        verificarAlertas();
    }

    private void lerTemperatura() {
        // Variação natural de temperatura (movimento browniano)
        double variacao = (random.nextDouble() - 0.5) * 2 * variacaoMaxima;
        temperaturaAtual += variacao;

        // Tendência periódica (simulando ciclos naturais)
        long cicloSegundos = (System.currentTimeMillis() / 1000) % 3600;
        double tendencia = Math.sin((cicloSegundos / 3600.0) * 2 * Math.PI) * 2;
        temperaturaAtual += tendencia * 0.01;

        // Limitar flutuações extremas
        temperaturaAtual = Math.max(-50, Math.min(150, temperaturaAtual));

        totalLeituras++;

        System.out.println("[Dispositivo " + dispositivoId + "]: " + "Leitura " + totalLeituras + " - Temperatura: " + temperaturaAtual + "°C");
    }

    private void enviarOperacaoSinglechain() throws Exception {
        try {
            String endereco = credenciais.getAddress();

            // ---- PASSO 1: o sensor envia a leitura -> o dispositivo chama a funcao do contrato ----
            // (a leitura ja foi gerada; observamos primeiro o status que o contrato retornaria)

            // Observacao dos PASSOS 2 e 4 (sem gastar gas): eth_call que simula any_operation
            // e retorna o codigo de status (cadastro/aceite ou bloqueio por penalidade).
            int status = consultarStatusOperacao();

            // ---- PASSO 2: o contrato identifica o dispositivo (cadastra na 1a vez) ----
            if (!cadastrado) {
                cadastrado = true;
                System.out.println("[Dispositivo " + dispositivoId + "]: PASSO 2 - dispositivo cadastrado no contrato (" + endereco + ")");
            }

            // ---- PASSO 4: avaliacao de comportamento (penalidade / bloqueio) ----
            if (status == STATUS_BLOQUEADO) {
                bloqueado = true;
                totalRejeitadas++;
                System.out.println("[Dispositivo " + dispositivoId + "]: PASSO 4 - BLOQUEADO (penalidade alta). Contrato rejeita a operacao.");
            } else if (status == STATUS_VERSAO_REJEITADA) {
                totalRejeitadas++;
                System.out.println("[Dispositivo " + dispositivoId + "]: PASSO 4 - operacao rejeitada (versao incompativel).");
            } else {
                bloqueado = false;
            }

            // ---- PASSOS 1 (efetivo) + 3 + 5: envia a transacao ----
            // O contrato registra a atividade (incrementa operacoes, guarda uso/comportamento)
            // e a EVM mede o gas usado na execucao.
            TransactionReceipt receipt = contrato.any_operation(BigInteger.ZERO).send();
            totalOperacoes++;
            gerenciador.registrarTransacao();

            // ---- PASSO 5: mede e acumula o gas usado (custo para pagamento futuro) ----
            BigInteger gas = receipt.getGasUsed();
            gerenciador.getMetricas().registrarOperacaoSinglechain(endereco, gas);

            // ---- PASSO 6: recompensa/penalidade ----
            // O contrato paga recompensas periodicamente (transfer) aos dispositivos bem
            // comportados; observamos o saldo do dispositivo como sinal da recompensa.
            try {
                saldoAtual = contrato.getUserBalance().send();
            } catch (Exception e) {
                // saldo indisponivel nesta iteracao
            }

            System.out.println("[Dispositivo " + dispositivoId + "]: PASSOS 3/5 - operacao registrada (gas: " + gas
                + ") | PASSO 6 - saldo/recompensa: " + saldoAtual + " wei");
        } catch (Exception e) {
            System.out.println("[Dispositivo " + dispositivoId + "]: Erro ao enviar operacao a EdgeChain: " + e.getMessage());
        }
    }

    private int consultarStatusOperacao() {
        try {
            Function function = new Function(
                EdgeChain.FUNC_ANY_OPERATION,
                Collections.<Type>singletonList(new Uint256(BigInteger.ZERO)),
                Collections.<TypeReference<?>>singletonList(new TypeReference<Uint256>() {}));
            String data = FunctionEncoder.encode(function);

            EthCall resp = web3j.ethCall(
                Transaction.createEthCallTransaction(credenciais.getAddress(), contratoAddr, data),
                DefaultBlockParameterName.LATEST).send();

            if (resp.hasError()) return -1;
            String value = resp.getValue();
            if (value == null || value.equals("0x") || value.isEmpty()) return -1;

            List<Type> out = FunctionReturnDecoder.decode(value, function.getOutputParameters());
            if (out.isEmpty()) return -1;
            return ((BigInteger) out.get(0).getValue()).intValue();
        } catch (Exception e) {
            return -1; // status desconhecido (nao interrompe o fluxo)
        }
    }

    private void verificarAlertas() {
        String tipoAlerta = null;

        if (temperaturaAtual > LIMITE_CRITICO_ALTO) {
            tipoAlerta = "CRÍTICO ALTO";
        } else if (temperaturaAtual < LIMITE_CRITICO_BAIXO) {
            tipoAlerta = "CRÍTICO BAIXO";
        } else if (temperaturaAtual > LIMITE_AVISO) {
            tipoAlerta = "AVISO";
        }

        if (tipoAlerta != null) {
            totalAlertas++;
            System.out.println("[Dispositivo " + dispositivoId + "]: ALERTA " + tipoAlerta + " detectado! Temp: " + String.format("%.2f°C", temperaturaAtual));
        }
    }

    public void stop() {
        ativo = false;
    }

    // Getters
    public int getDispositivoId() {
        return dispositivoId;
    }

    public double getTemperaturaAtual() {
        return temperaturaAtual;
    }

    public int getTotalLeituras() {
        return totalLeituras;
    }

    public int getTotalAlertas() {
        return totalAlertas;
    }
}
