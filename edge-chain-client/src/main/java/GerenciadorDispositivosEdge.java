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
 * GerenciadorDispositivosEdge  ---  ORQUESTRADOR DA ARQUITETURA EDGE (SINGLECHAIN)
 * ============================================================================
 *
 * Espelha o GerenciadorDispositivosSingle (mesma chain unica, mesmo contrato
 * EdgeChain, mesmo financiamento de contrato/dispositivos e mesma janela de
 * monitoramento), inserindo a camada EDGE NODE entre os sensores e o contrato:
 *
 *      DispositivoIoTEdge  ->  EdgeNode  ->  EdgeChain.sol  ->  Ganache
 *
 * Diferencas em relacao ao Singlechain:
 *   - cria um unico EdgeNode compartilhado (com BatchManager + AlertDetector);
 *   - registra os dispositivos no EdgeNode (um contrato por dispositivo, com as
 *     credenciais do dispositivo, preservando o msg.sender no contrato);
 *   - roda um scheduler que dispara o gatilho por TEMPO (flush das janelas);
 *   - ao encerrar, consolida os batches residuais (nao perde leituras).
 *
 * O financiamento e o coletor de metricas sao os MESMOS da Singlechain, para
 * permitir comparacao direta entre as arquiteturas.
 * ============================================================================
 */
public class GerenciadorDispositivosEdge {
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final List<DispositivoIoTEdge> dispositivos;

    private final Web3j web3j;            // chain unica
    private final String contratoAddr;   // endereco do EdgeChain

    private final EdgeNode edgeNode;
    private final BatchManager batchManager;
    private final AlertDetector alertDetector;

    // Intervalo (s) do gatilho por TEMPO do Edge Node (item (b) da politica).
    private final long intervaloFlush;

    // Coletor de metricas (o MESMO usado pela single/dualchain, para dados comparaveis).
    private final MetricasExecucao metricas = new MetricasExecucao();

    public MetricasExecucao getMetricas() { return metricas; }

    // Mesmas chaves privadas da Singlechain (comparacao justa: mesmos dispositivos).
    private static final String[] PRIVATE_KEYS = {
        "0x75d1ef2dc48046b401e1102ebe76a86be9d2a41e08d6195207aba9b438b79053", // Dispositivo 0
        "0x7497b916cff84e64947b77a9e6aeeaa40a75fe3cbe212e44faaa8bc8c0562d05", // Dispositivo 1
        "0x6d3939c696920b08f0ead2adef0e6185c6d8be46733c43d733ac84a143bb2e17", // Dispositivo 2
        "0x3ae43174617245eea835fb3f52c9e4e04c521cf769a1085a2562bbf3631eb01e", // Dispositivo 3
        "0xdd1a432115a4fd270ab6d90bc29f97b983df78ab96163b0bb20bdf5c9e148dcf", // Dispositivo 4
    };

    public GerenciadorDispositivosEdge(String rpcUrl, String contratoAddr,
                                       int loteMin, long janelaSegundos) {
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.contratoAddr = contratoAddr;
        this.executor = Executors.newFixedThreadPool(5);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.dispositivos = new ArrayList<>();
        this.intervaloFlush = janelaSegundos;

        // Politica de agregacao do Edge Node.
        this.batchManager  = new BatchManager(loteMin, janelaSegundos * 1000L);
        this.alertDetector = new AlertDetector(); // limiares 45 / -10 (alinhados ao cliente/contrato)
        this.edgeNode      = new EdgeNode(web3j, contratoAddr, batchManager, alertDetector, metricas);

        verificarConexao();
    }

    private void verificarConexao() {
        try {
            String version = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            System.out.println("Single Chain (EdgeChain) conectada: " + version);
        } catch (Exception e) {
            System.err.println("Erro na conexão: " + e.getMessage());
            System.exit(1);
        }
    }

    // --- Financiamento: identico ao GerenciadorDispositivosSingle ---

    public void financiarContrato() {
        try {
            Credentials funder = Credentials.create(PRIVATE_KEYS[0]);

            BigInteger saldoFunder = web3j.ethGetBalance(funder.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
            BigInteger saldoContratoAntes = web3j.ethGetBalance(contratoAddr, DefaultBlockParameterName.LATEST).send().getBalance();
            System.out.println("Financiando contrato EdgeChain (" + contratoAddr + ") com 1 ETHER a partir de " + funder.getAddress() + "...");
            System.out.println("  Saldo do funder     : " + Convert.fromWei(new BigDecimal(saldoFunder), Convert.Unit.ETHER) + " ETH");
            System.out.println("  Saldo do contrato   : " + Convert.fromWei(new BigDecimal(saldoContratoAntes), Convert.Unit.ETHER) + " ETH (antes)");

            if (saldoFunder.compareTo(Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigInteger()) < 0) {
                System.err.println("  [ATENCAO] Funder tem menos de 1 ETH; o financiamento pode falhar e o payout do contrato vai reverter.");
            }

            Transfer transfer = new Transfer(web3j, new RawTransactionManager(web3j, funder));
            TransactionReceipt receipt = transfer.sendFunds(
                contratoAddr, BigDecimal.ONE, Convert.Unit.ETHER,
                BigInteger.valueOf(20_000_000_000L), // gasPrice: 20 gwei
                BigInteger.valueOf(100_000L)         // gasLimit: suficiente p/ o receive() do contrato
            ).send();

            BigInteger saldoContratoDepois = web3j.ethGetBalance(contratoAddr, DefaultBlockParameterName.LATEST).send().getBalance();
            System.out.println("Contrato financiado. tx=" + receipt.getTransactionHash()
                + " | saldo do contrato agora: " + Convert.fromWei(new BigDecimal(saldoContratoDepois), Convert.Unit.ETHER) + " ETH");

            if (saldoContratoDepois.signum() == 0) {
                System.err.println("  [ERRO] Contrato continua com saldo 0 apos o financiamento — o payout de any_operation vai reverter e a execucao vai parar cedo.");
            }
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao financiar o contrato (o payout vai reverter e o edge vai parar cedo): " + e.getMessage());
        }
    }

    private static final BigDecimal VALOR_POR_DISPOSITIVO_ETHER = BigDecimal.ONE; // 1 ETHER/dispositivo

    public void financiarDispositivos() {
        try {
            Credentials funder = Credentials.create(PRIVATE_KEYS[0]);

            BigInteger saldoFunder = web3j.ethGetBalance(funder.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();
            System.out.println("Financiando contas dos dispositivos com " + VALOR_POR_DISPOSITIVO_ETHER
                + " ETHER cada, a partir de " + funder.getAddress() + " (funder = Dispositivo 0)...");
            System.out.println("  Saldo do funder: " + Convert.fromWei(new BigDecimal(saldoFunder), Convert.Unit.ETHER) + " ETH");

            for (int i = 1; i < PRIVATE_KEYS.length; i++) {
                String destino = Credentials.create(PRIVATE_KEYS[i]).getAddress();
                try {
                    TransactionReceipt receipt = Transfer.sendFunds(
                        web3j, funder, destino,
                        VALOR_POR_DISPOSITIVO_ETHER, Convert.Unit.ETHER
                    ).send();
                    BigInteger saldoDepois = web3j.ethGetBalance(destino, DefaultBlockParameterName.LATEST).send().getBalance();
                    System.out.println("  Dispositivo-" + i + " (" + destino + ") financiado. tx=" + receipt.getTransactionHash()
                        + " | saldo agora: " + Convert.fromWei(new BigDecimal(saldoDepois), Convert.Unit.ETHER) + " ETH");
                } catch (Exception e) {
                    System.err.println("  [ERRO] Falha ao financiar Dispositivo-" + i + " (" + destino + "): " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao financiar os dispositivos: " + e.getMessage());
        }
    }

    public void iniciarDispositivos() {
        System.out.println("\n=== Iniciando 5 Dispositivos IoT (EDGE) ===\n");
        System.out.println("Politica de batch: loteMin=" + batchManager.getLoteMin()
                + " leituras | janela=" + (batchManager.getJanelaMs() / 1000) + "s"
                + " | limites criticos=" + alertDetector.getLimiteCriticoAlto()
                + "/" + alertDetector.getLimiteCriticoBaixo() + "\n");

        for (int i = 0; i < 5; i++) {
            try {
                Credentials credenciais = Credentials.create(PRIVATE_KEYS[i]);
                // Registra o dispositivo no Edge Node (cria buffer + contrato com msg.sender do dispositivo).
                edgeNode.registrarDispositivo(i, credenciais);

                DispositivoIoTEdge dispositivo = new DispositivoIoTEdge(i, edgeNode);
                dispositivos.add(dispositivo);
                executor.submit(dispositivo);
                System.out.println("Dispositivo-" + i + " iniciado com: " + credenciais.getAddress());
            } catch (Exception e) {
                System.err.println("Erro ao iniciar Dispositivo-" + i + ": " + e.getMessage());
            }
        }

        iniciarScheduler();
    }

    /** Gatilho por TEMPO: periodicamente verifica janelas vencidas no Edge Node. */
    private void iniciarScheduler() {
        System.out.println("=== Monitor do EDGE NODE (flush por tempo a cada " + intervaloFlush + "s) ===\n");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                edgeNode.flushTodos(false); // respeita a politica (envia apenas janelas vencidas)
            } catch (Exception e) {
                System.err.println("✗ Erro no monitor do Edge Node: " + e.getMessage());
            }
        }, intervaloFlush, intervaloFlush, TimeUnit.SECONDS);
    }

    public void pararDispositivos() {
        System.out.println("\n=== Parando todos os dispositivos ===\n");

        dispositivos.forEach(DispositivoIoTEdge::stop);

        // Consolida os batches residuais antes de encerrar (nao perde leituras).
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
        System.out.println("ESTATÍSTICAS DO GERENCIADOR (EDGE)");
        System.out.println("=".repeat(60));
        System.out.println("Total de Transações (Edge Chain): " + metricas.getEdgeBatches());
        System.out.println("Dispositivos Ativos: " + dispositivos.size());
        System.out.println("=".repeat(60) + "\n");

        metricas.imprimirRelatorioEdge();
    }
}
