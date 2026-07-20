import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

 
public class GerenciadorDispositivos {
    private ExecutorService executor;
    private List<DispositivoIoT> dispositivos;

    // Dual Chain
    private ScheduledExecutorService schedulerSincronizacao;
    private Web3j sideChainWeb3j;  
    private Web3j mainChainWeb3j;
    private String sideChainContratoAddr;
    private String mainChainContratoAddr;
    private long intervaloSincronizacao;
    private Credentials credenciaisRelayer;

    // Contratos usados pelo RELAYER
    private EdgechainMain mainContract;
    private EdgechainRegulator reguladorRelay;

    // Estatísticas
    private volatile int contadorTransacoes = 0;
    private volatile int contadorRelays = 0;
    private volatile int contadorCriticos = 0;

    private final MetricasExecucao metricas = new MetricasExecucao();

    public MetricasExecucao getMetricas() { return metricas;}

    // ----------------------------------------------------------------------
    // AGREGACAO (BATCH) + FILTRAGEM INTELIGENTE (nova arquitetura dualchain).
    //
    // Em vez de encaminhar 1 transacao para a MAINCHAIN por leitura aprovada, o
    // relayer ACUMULA as leituras de cada dispositivo na sidechain (em memoria)
    // e so toca a mainchain quando ocorre UMA das condicoes abaixo:
    //   (a) quantidade minima de leituras atingida  -> loteMin
    //   (b) intervalo de tempo atingido             -> intervaloSincronizacao (scheduler)
    //   (c) evento critico (temperatura > limite)   -> flush imediato
    //   (d) mudanca significativa de temperatura     -> deltaSignificativo
    // Assim a MAINCHAIN recebe apenas RESUMOS CONSOLIDADOS e eventos criticos.
    // ----------------------------------------------------------------------
    private final Map<String, Batch> batches = new ConcurrentHashMap<>();
    private int loteMin = 50;            // (a) qtd minima de leituras para consolidar um batch
    private int deltaSignificativo = 5;  // (d) variacao de temperatura (C) considerada relevante
    private int limiteCritico = 45;      // (c) temperatura critica (alinhado a EdgechainMain)
    private volatile int contadorLeiturasAgregadas = 0; // leituras absorvidas na sidechain sem tocar a mainchain

    /** Torna os gatilhos de batch facilmente configuraveis a partir do runner. */
    public void configurarBatch(int loteMin, int deltaSignificativo, int limiteCritico) {
        this.loteMin = loteMin;
        this.deltaSignificativo = deltaSignificativo;
        this.limiteCritico = limiteCritico;
    }

    /**
     * Estatisticas agregadas por dispositivo, mantidas em memoria no relayer
     * (nao ha nova estrutura on-chain: reaproveitamos a chamada Web3j existente
     * executeTemperatureOperation para enviar o resumo consolidado).
     */
    private static class Batch {
        long       count;         // quantidade de leituras acumuladas desde o ultimo envio
        BigInteger min;           // temperatura minima do batch
        BigInteger max;           // temperatura maxima do batch (usada p/ deteccao de critico)
        BigInteger last;          // ultima temperatura
        BigInteger sum;           // soma (para a media = sum / count)
        BigInteger primeiroTs;    // timestamp inicial do batch
        BigInteger ultimoTs;      // timestamp final do batch
        long       operationType; // ultimo tipo de operacao
        BigInteger ultimaEnviada; // ultima temperatura efetivamente enviada a mainchain
    }

    // Preenchimento das chaves privadas de acordo com o fornecimento da rede sidechain
    private static final String[] PRIVATE_KEYS = {
        "0xf5cfb7f145e2524edb3b2a92a71f05746a1edafd549c9f19878358494c88d68c", // Dispositivo 0
        "0x7502d307d6e8b14e2b5031029f41aa155ad257f65854c66dd0fc36cfbd87d241", // Dispositivo 1
        "0xae8b83183c3744e1368a885734a0502756baae9595dd433d4ebe1f51dd1884d7", // Dispositivo 2
        "0x792943ac439af11f1e795a11b829721b4475924bc3b9713f83be98a20757c842", // Dispositivo 3
        "0x64a4b9dc5a4f7bee83ec7d44a14a5736c4b488cc141f9c01352e49a869b84842", // Dispositivo 4
    };

    public GerenciadorDispositivos(String sideChainRpcUrl, String mainChainRpcUrl,
                                   String sideChainContratoAddr, String mainChainContratoAddr,
                                  long intervaloSincronizacao, Credentials credenciaisRelayer) {

        // Inicializar conexões com as chains
        this.sideChainWeb3j = Web3j.build(new HttpService(sideChainRpcUrl));
        this.mainChainWeb3j = Web3j.build(new HttpService(mainChainRpcUrl));

        this.sideChainContratoAddr = sideChainContratoAddr;
        this.mainChainContratoAddr = mainChainContratoAddr;
        this.executor = Executors.newFixedThreadPool(5);
        this.schedulerSincronizacao = Executors.newScheduledThreadPool(1);
        this.dispositivos = new ArrayList<>();
        this.intervaloSincronizacao = intervaloSincronizacao;
        this.credenciaisRelayer = credenciaisRelayer;

        verificarConexoes();

        // Contrato principal (mainchain)
        this.mainContract = EdgechainMain.load(
            mainChainContratoAddr,
            mainChainWeb3j,
            credenciaisRelayer,
            BigInteger.valueOf(20_000_000_000L),
            BigInteger.valueOf(500_000)
        );

        // Regulador (sidechain)
        this.reguladorRelay = EdgechainRegulator.load(
            sideChainContratoAddr,
            sideChainWeb3j,
            credenciaisRelayer,
            BigInteger.valueOf(20_000_000_000L),
            BigInteger.valueOf(500_000)
        );
    }

    private void verificarConexoes() {
        try {
            String sideChainVersion = sideChainWeb3j.web3ClientVersion().send().getWeb3ClientVersion();
            String mainChainVersion = mainChainWeb3j.web3ClientVersion().send().getWeb3ClientVersion();

            System.out.println("Side Chain (Regulador) conectada: " + sideChainVersion);
            System.out.println("Main Chain (Principal) conectada: " + mainChainVersion);
        } catch (Exception e) {
            System.err.println("Erro na conexão: " + e.getMessage());
            System.exit(1);
        }
    }

    public void financiarContrato() {
        try {
            Credentials funder = Credentials.create(PRIVATE_KEYS[0]);

            BigInteger saldoFunder = sideChainWeb3j.ethGetBalance(funder.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
            BigInteger saldoContratoAntes = sideChainWeb3j.ethGetBalance(sideChainContratoAddr, DefaultBlockParameterName.LATEST).send().getBalance();
            System.out.println("Financiando REGULADOR (sidechain) " + sideChainContratoAddr + " com 1 ETHER a partir de " + funder.getAddress() + "...");
            System.out.println("  Saldo do funder     : " + Convert.fromWei(new BigDecimal(saldoFunder), Convert.Unit.ETHER) + " ETH");
            System.out.println("  Saldo do regulador  : " + Convert.fromWei(new BigDecimal(saldoContratoAntes), Convert.Unit.ETHER) + " ETH (antes)");

            if (saldoFunder.compareTo(Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigInteger()) < 0) {
                System.err.println("  [ATENCAO] Funder tem menos de 1 ETH; o financiamento pode falhar e o payout do regulador vai reverter.");
            }

            Transfer transfer = new Transfer(sideChainWeb3j, new RawTransactionManager(sideChainWeb3j, funder));
            TransactionReceipt receipt = transfer.sendFunds(
                sideChainContratoAddr, BigDecimal.ONE, Convert.Unit.ETHER,
                BigInteger.valueOf(20_000_000_000L), // gasPrice: 20 gwei
                BigInteger.valueOf(100_000L) // gasLimit
            ).send();

            BigInteger saldoContratoDepois = sideChainWeb3j.ethGetBalance(sideChainContratoAddr, DefaultBlockParameterName.LATEST).send().getBalance();
            System.out.println("Regulador financiado. tx=" + receipt.getTransactionHash()
                + " | saldo do regulador agora: " + Convert.fromWei(new BigDecimal(saldoContratoDepois), Convert.Unit.ETHER) + " ETH");

            if (saldoContratoDepois.signum() == 0) {
                System.err.println("  [ERRO] Regulador continua com saldo 0 apos o financiamento — o payout (transfer) vai reverter e a execucao vai parar cedo.");
            }
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao financiar o regulador (o payout vai reverter e a dualchain vai parar cedo): " + e.getMessage());
        }
    }

    public void iniciarDispositivos() {
        System.out.println("\n=== Iniciando 5 Dispositivos IoT (DUALCHAIN) ===\n");

        for (int i = 0; i < 5; i++) {
            try {
                Credentials credenciais = Credentials.create(PRIVATE_KEYS[i]);
                boolean malicioso = (i == 4);
                DispositivoIoT dispositivo = new DispositivoIoT(i, sideChainWeb3j, credenciais, sideChainContratoAddr, this, malicioso);
                dispositivos.add(dispositivo);
                executor.submit(dispositivo);
                System.out.println("Dispositivo-" + i + " iniciado com: " + credenciais.getAddress());

            } catch (Exception e) {
                System.err.println("Erro ao iniciar Dispositivo-" + i + ": " + e.getMessage());
            }
        }
    }

    public void pararDispositivos() {
        System.out.println("\n=== Parando todos os dispositivos ===\n");

        dispositivos.forEach(DispositivoIoT::stop);

        // Consolida os batches residuais antes de encerrar (nao perde leituras).
        flushTodosBatches();

        executor.shutdown();
        schedulerSincronizacao.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
            if (!schedulerSincronizacao.awaitTermination(10, TimeUnit.SECONDS)) schedulerSincronizacao.shutdownNow();

        } catch (InterruptedException e) {
            executor.shutdownNow();
            schedulerSincronizacao.shutdownNow();
        }
    }

    public void monitorarDuracao(long durationSeconds) {
        try {
            Thread.sleep(durationSeconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        pararDispositivos();
    }

    /**
     * FILTRAGEM INTELIGENTE + AGREGACAO.
     *
     * Mantem a MESMA assinatura publica de antes (o DispositivoIoT nao muda), mas
     * o comportamento passa a ser: a leitura aprovada e ACUMULADA no batch do
     * dispositivo e a MAINCHAIN so e acionada quando um gatilho e disparado
     * (critico / lote cheio / mudanca significativa). O gatilho por TEMPO fica a
     * cargo do scheduler (flushTodosBatches). Enquanto nao dispara, a leitura e
     * absorvida na sidechain e retornamos false (nenhuma tx de mainchain).
     *
     * Retorna true se o envio consolidado gerou ALERTA CRITICO (para o
     * dispositivo registrar a resposta corretamente).
     */
    public synchronized boolean encaminharParaMainchain(String deviceAddress, long operationType,
                                                        BigInteger temperature, BigInteger timestamp) {
        Batch b = batches.computeIfAbsent(deviceAddress, k -> new Batch());

        // Inicio de um novo batch (primeira leitura apos um flush): reinicia agregados.
        if (b.count == 0) {
            b.min = temperature;
            b.max = temperature;
            b.sum = BigInteger.ZERO;
            b.primeiroTs = timestamp;
        } else {
            if (temperature.compareTo(b.min) < 0) b.min = temperature;
            if (temperature.compareTo(b.max) > 0) b.max = temperature;
        }
        b.count++;
        b.last = temperature;
        b.sum = b.sum.add(temperature);
        b.ultimoTs = timestamp;
        b.operationType = operationType;
        contadorLeiturasAgregadas++;

        // --- Gatilhos de envio (filtragem inteligente) ---
        boolean critico = temperature.intValue() > limiteCritico;                 // (c) evento critico
        boolean loteCheio = b.count >= loteMin;                                    // (a) qtd minima
        boolean mudancaSignificativa = b.ultimaEnviada != null                     // (d) delta relevante
                && temperature.subtract(b.ultimaEnviada).abs().intValue() >= deltaSignificativo;

        if (critico || loteCheio || mudancaSignificativa) {
            return flushBatch(deviceAddress, b);
        }
        // Leitura NORMAL: apenas estatistica na sidechain; mainchain nao e tocada.
        return false;
    }

    /**
     * Consolida o batch de um dispositivo e envia UM resumo para a MAINCHAIN,
     * reaproveitando a chamada Web3j existente executeTemperatureOperation:
     *   operationType <- quantidade de leituras consolidadas;
     *   temperature   <- temperatura MAXIMA do batch (preserva a deteccao de
     *                    critico na mainchain, que compara temp > limite);
     *   timestamp     <- timestamp final do batch.
     * O resumo completo (min/media/janela) e registrado em log e nas metricas.
     */
    private synchronized boolean flushBatch(String deviceAddress, Batch b) {
        if (b == null || b.count == 0) return false;
        try {
            BigInteger media = b.sum.divide(BigInteger.valueOf(b.count));

            // --- MAINCHAIN: recebe apenas o RESUMO consolidado ---
            TransactionReceipt receipt = mainContract.executeTemperatureOperation(
                deviceAddress,
                BigInteger.valueOf(b.count), // qtd de leituras (resumo, nao 1-a-1)
                b.max,                        // pior caso -> deteccao de temperatura critica
                b.ultimoTs
            ).send();

            BigInteger gasUsado = receipt.getGasUsed();
            contadorRelays++;
            metricas.registrarLeituraMainchain(deviceAddress, gasUsado);

            System.out.println(">>> BATCH -> MAINCHAIN device=" + deviceAddress
                + " | leituras=" + b.count + " min=" + b.min + " max=" + b.max
                + " media=" + media + " ultima=" + b.last
                + " tsIni=" + b.primeiroTs + " tsFim=" + b.ultimoTs
                + " (gas mainchain: " + gasUsado + ")");

            List<EdgechainMain.CriticalAlertEventResponse> alertas =
                mainContract.getCriticalAlertEvents(receipt);
            boolean alertaCritico = false;
            for (EdgechainMain.CriticalAlertEventResponse a : alertas) {
                alertaCritico = true;
                contadorCriticos++;
                System.out.println(">>> MAINCHAIN: ALERTA CRITICO (batch) device=" + a.device
                    + " tempMax=" + a.temperature + " leituras=" + b.count);
            }

            // --- SIDECHAIN: recebe o retorno (custo consolidado) ---
            try {
                reguladorRelay.updateExecutionCost(deviceAddress, true, gasUsado).send();
            } catch (Exception e) {
                System.err.println("Relayer: falha ao atualizar custo na sidechain: " + e.getMessage());
            }

            // Reinicia o batch (a proxima leitura recomeca a agregacao).
            b.ultimaEnviada = b.last;
            b.count = 0;
            return alertaCritico;

        } catch (Exception e) {
            System.err.println("Relayer: falha ao encaminhar batch para a mainchain: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gatilho por TEMPO (item 3): consolida periodicamente todos os batches
     * pendentes. Chamado pelo scheduler e no encerramento (para nao perder
     * leituras residuais).
     */
    public synchronized void flushTodosBatches() {
        for (Map.Entry<String, Batch> e : batches.entrySet()) {
            if (e.getValue().count > 0) {
                flushBatch(e.getKey(), e.getValue());
            }
        }
    }

    public void iniciarSincronizacao() {
        System.out.println("\n=== Monitor do RELAYER (SIDE CHAIN -> MAIN CHAIN) a cada " + intervaloSincronizacao + "s ===\n");

        schedulerSincronizacao.scheduleAtFixedRate(() -> {
            try {
                // Gatilho por TEMPO: consolida os batches pendentes periodicamente.
                flushTodosBatches();

                System.out.println("\n>>> STATUS RELAYER <<<");
                System.out.println("Transacoes na sidechain: " + contadorTransacoes);
                System.out.println("Leituras agregadas (absorvidas na sidechain): " + contadorLeiturasAgregadas);
                System.out.println("Batches encaminhados a mainchain: " + contadorRelays);
                System.out.println("Alertas criticos na mainchain: " + contadorCriticos);
            } catch (Exception e) {
                System.err.println("✗ Erro no monitor do relayer: " + e.getMessage());
            }
        }, intervaloSincronizacao, intervaloSincronizacao, TimeUnit.SECONDS);
    }

    public void registrarTransacao() { contadorTransacoes++; }

    public void exibirEstatisticas() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ESTATÍSTICAS DO GERENCIADOR (DUALCHAIN)");
        System.out.println("=".repeat(60));
        System.out.println("Total de Transações (Side Chain): " + contadorTransacoes);
        System.out.println("Leituras agregadas na Side Chain: " + contadorLeiturasAgregadas);
        System.out.println("Batches encaminhados (Main Chain): " + contadorRelays);
        System.out.println("Alertas críticos (Main Chain): " + contadorCriticos);
        if (contadorRelays > 0) {
            System.out.printf("Fator de agregacao (leituras por batch): %.1f%n",
                    (double) contadorLeiturasAgregadas / contadorRelays);
        }
        System.out.println("Dispositivos Ativos: " + dispositivos.size());
        System.out.println("=".repeat(60) + "\n");

        exibirRelatorioMetricas();
    }

    public void exibirRelatorioMetricas() { metricas.imprimirRelatorio(); }
}
