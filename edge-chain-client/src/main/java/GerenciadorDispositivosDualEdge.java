import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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

/**
 * ============================================================================
 * GerenciadorDispositivosDualEdge  ---  ORQUESTRADOR DA DUALCHAIN COM EDGE NODE
 * ============================================================================
 *
 * Espelha o GerenciadorDispositivos (dualchain): mesmas duas chains, mesmos
 * contratos (EdgechainRegulator na sidechain, EdgechainMain na mainchain), mesmo
 * financiamento do regulador e mesmo relayer. A diferenca e o FLUXO DE ENVIO:
 * agora os dispositivos entregam as leituras ao EDGE NODE, que agrega, calcula o
 * hash SHA-256 e envia apenas o RESUMO do lote a sidechain (e o hash a mainchain).
 *
 *      DispositivoIoTDualEdge -> EdgeNodeDual -> Sidechain -> Mainchain
 *
 * O coletor de metricas e o MESMO da dualchain original (side/main + agregacao),
 * permitindo comparacao direta.
 * ============================================================================
 */
public class GerenciadorDispositivosDualEdge {
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final List<DispositivoIoTDualEdge> dispositivos;

    private final Web3j sideChainWeb3j;
    private final Web3j mainChainWeb3j;
    private final String sideChainContratoAddr;
    private final String mainChainContratoAddr;
    private final long intervaloFlush;
    private final Credentials credenciaisRelayer;

    private final EdgechainMain mainContract;        // relayer
    private final EdgechainRegulator reguladorRelay; // relayer

    private final EdgeNodeDual edgeNode;
    private final BatchManager batchManager;
    private final AlertDetector alertDetector;

    private final MetricasExecucao metricas = new MetricasExecucao();
    public MetricasExecucao getMetricas() { return metricas; }

    // Mesmas chaves privadas da dualchain (contas da SIDECHAIN, porta 8545).
    private static final String[] PRIVATE_KEYS = {
        "0xc7412a9632e3a4322329ec2800d06d0b9b35e6df7ebb6e3659960b5ff14558f1", // Dispositivo 0
        "0x1b6e1dce5e0c4946743de3cab6a0090de664ad5795ecf8f4f4b9023b9f9e0937", // Dispositivo 1
        "0x07172c36a5a38fae30b38f1d89726da7d571f4c9cbd1edaed923768ee11f5319", // Dispositivo 2
        "0x57bad4b2050da9b284cc8241b47c00997f455f53e7603df31865ab35f5637590", // Dispositivo 3
        "0x463c58318b00f0c26a9b2850c4e03ab2c325b58fb33e29b38e3ab5470c9d2e69", // Dispositivo 4
    };

    public GerenciadorDispositivosDualEdge(String sideChainRpcUrl, String mainChainRpcUrl,
                                           String sideChainContratoAddr, String mainChainContratoAddr,
                                           long janelaSegundos, Credentials credenciaisRelayer,
                                           int loteMin) {
        this.sideChainWeb3j = Web3j.build(new HttpService(sideChainRpcUrl));
        this.mainChainWeb3j = Web3j.build(new HttpService(mainChainRpcUrl));
        this.sideChainContratoAddr = sideChainContratoAddr;
        this.mainChainContratoAddr = mainChainContratoAddr;
        this.executor = Executors.newFixedThreadPool(5);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.dispositivos = new ArrayList<>();
        this.intervaloFlush = janelaSegundos;
        this.credenciaisRelayer = credenciaisRelayer;

        verificarConexoes();

        this.mainContract = EdgechainMain.load(
            mainChainContratoAddr, mainChainWeb3j, credenciaisRelayer,
            BigInteger.valueOf(20_000_000_000L), BigInteger.valueOf(500_000));

        this.reguladorRelay = EdgechainRegulator.load(
            sideChainContratoAddr, sideChainWeb3j, credenciaisRelayer,
            BigInteger.valueOf(20_000_000_000L), BigInteger.valueOf(500_000));

        this.batchManager  = new BatchManager(loteMin, janelaSegundos * 1000L);
        this.alertDetector = new AlertDetector(); // limites 45 / -10 (alinhados ao contrato)
        this.edgeNode = new EdgeNodeDual(sideChainWeb3j, sideChainContratoAddr,
                mainContract, reguladorRelay, batchManager, alertDetector, metricas);
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

    /** Financiamento do REGULADOR (sidechain) -- identico a dualchain original. */
    public void financiarContrato() {
        try {
            Credentials funder = Credentials.create(PRIVATE_KEYS[0]);
            BigInteger saldoFunder = sideChainWeb3j.ethGetBalance(funder.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
            BigInteger saldoContratoAntes = sideChainWeb3j.ethGetBalance(sideChainContratoAddr, DefaultBlockParameterName.LATEST).send().getBalance();
            System.out.println("Financiando REGULADOR (sidechain) " + sideChainContratoAddr + " com 1 ETHER a partir de " + funder.getAddress() + "...");
            System.out.println("  Saldo do funder     : " + Convert.fromWei(new BigDecimal(saldoFunder), Convert.Unit.ETHER) + " ETH");
            System.out.println("  Saldo do regulador  : " + Convert.fromWei(new BigDecimal(saldoContratoAntes), Convert.Unit.ETHER) + " ETH (antes)");

            Transfer transfer = new Transfer(sideChainWeb3j, new RawTransactionManager(sideChainWeb3j, funder));
            TransactionReceipt receipt = transfer.sendFunds(
                sideChainContratoAddr, BigDecimal.ONE, Convert.Unit.ETHER,
                BigInteger.valueOf(20_000_000_000L), BigInteger.valueOf(100_000L)
            ).send();

            BigInteger saldoContratoDepois = sideChainWeb3j.ethGetBalance(sideChainContratoAddr, DefaultBlockParameterName.LATEST).send().getBalance();
            System.out.println("Regulador financiado. tx=" + receipt.getTransactionHash()
                + " | saldo do regulador agora: " + Convert.fromWei(new BigDecimal(saldoContratoDepois), Convert.Unit.ETHER) + " ETH");
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao financiar o regulador: " + e.getMessage());
        }
    }

    public void iniciarDispositivos() {
        System.out.println("\n=== Iniciando 5 Dispositivos IoT (DUALCHAIN EDGE) ===\n");
        System.out.println("Politica de batch: loteMin=" + batchManager.getLoteMin()
                + " leituras | janela=" + (batchManager.getJanelaMs() / 1000) + "s\n");

        for (int i = 0; i < 5; i++) {
            try {
                Credentials credenciais = Credentials.create(PRIVATE_KEYS[i]);
                String deviceId = "sensor-" + i;
                edgeNode.registrarDispositivo(i, deviceId, credenciais);

                boolean malicioso = (i == 4);
                DispositivoIoTDualEdge dispositivo = new DispositivoIoTDualEdge(i, edgeNode, malicioso);
                dispositivos.add(dispositivo);
                executor.submit(dispositivo);
                System.out.println("Dispositivo-" + i + " iniciado com: " + credenciais.getAddress()
                        + (malicioso ? " [MALICIOSO]" : ""));
            } catch (Exception e) {
                System.err.println("Erro ao iniciar Dispositivo-" + i + ": " + e.getMessage());
            }
        }

        iniciarScheduler();
    }

    /** Gatilho por TEMPO: fecha periodicamente os lotes com janela vencida. */
    private void iniciarScheduler() {
        System.out.println("=== Monitor do EDGE NODE (flush por tempo a cada " + intervaloFlush + "s) ===\n");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                edgeNode.flushTodos(false);
                System.out.println("\n>>> STATUS EDGE NODE <<<");
                System.out.println("Leituras recebidas   : " + metricas.getEdgeLeiturasRecebidas());
                System.out.println("Batches enviados     : " + edgeNode.getContadorBatches());
                System.out.println("Batches aprovados    : " + edgeNode.getContadorAprovados());
                System.out.println("Alertas criticos     : " + edgeNode.getContadorCriticos());
                System.out.println("Bloqueios (sidechain): " + edgeNode.getContadorBloqueios());
            } catch (Exception e) {
                System.err.println("✗ Erro no monitor do Edge Node: " + e.getMessage());
            }
        }, intervaloFlush, intervaloFlush, TimeUnit.SECONDS);
    }

    public void pararDispositivos() {
        System.out.println("\n=== Parando todos os dispositivos ===\n");
        dispositivos.forEach(DispositivoIoTDualEdge::stop);

        // Consolida os lotes residuais antes de encerrar (nao perde leituras).
        edgeNode.flushTodos(true);

        executor.shutdown();
        scheduler.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            scheduler.shutdownNow();
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

    public void exibirEstatisticas() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ESTATÍSTICAS DO GERENCIADOR (DUALCHAIN EDGE)");
        System.out.println("=".repeat(60));
        System.out.println("Leituras recebidas pelo Edge Node : " + metricas.getEdgeLeiturasRecebidas());
        System.out.println("Batches enviados a Sidechain      : " + edgeNode.getContadorBatches());
        System.out.println("Batches aprovados (-> Mainchain)  : " + edgeNode.getContadorAprovados());
        System.out.println("Alertas criticos (Mainchain)      : " + edgeNode.getContadorCriticos());
        System.out.println("Bloqueios temporarios (Sidechain) : " + edgeNode.getContadorBloqueios());
        System.out.println("Dispositivos Ativos               : " + dispositivos.size());
        System.out.println("=".repeat(60) + "\n");

        // Relatorio dualchain (side/main) + relatorio de agregacao do Edge.
        metricas.imprimirRelatorio();
        metricas.imprimirRelatorioEdge();
    }
}
