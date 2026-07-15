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

    // Preenchimento das chaves privadas de acordo com o fornecimento da rede sidechain
    private static final String[] PRIVATE_KEYS = {
        "0xca9dbf37cf7472ca6461c05bc061d67f85e11e3702c6bbfe6deaf21fd58d5ef7", // Dispositivo 0
        "0x6a161eaf586742309431f73c18d1affe8dd7f0d9a8e3e354d90a9cff3cfc9841", // Dispositivo 1
        "0x12e8e566ab0ee525c3650f8d8a3352c15489b3244a58e6129193339d88126aa0", // Dispositivo 2
        "0xebec49d21d31289e7d0858c5c985d925ef6f5bc4653bb902fc9290f76d5b293c", // Dispositivo 3
        "0x167876a201aa1413eeb00e111d6e2a3ef6f73bdc7bb4deec6dc3386eb390a1da", // Dispositivo 4
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

    public synchronized boolean encaminharParaMainchain(String deviceAddress, long operationType,
                                                        BigInteger temperature, BigInteger timestamp) {
        try {
            // --- MAINCHAIN: executa a operacao definitiva ---
            TransactionReceipt receipt = mainContract.executeTemperatureOperation(
                deviceAddress,
                BigInteger.valueOf(operationType),
                temperature,
                timestamp
            ).send();

            BigInteger gasUsado = receipt.getGasUsed();
            contadorRelays++;
            metricas.registrarLeituraMainchain(deviceAddress, gasUsado);

            List<EdgechainMain.CriticalAlertEventResponse> alertas =
                mainContract.getCriticalAlertEvents(receipt);
            boolean alertaCritico = false;
            for (EdgechainMain.CriticalAlertEventResponse a : alertas) {
                alertaCritico = true;
                contadorCriticos++;
                System.out.println(">>> MAINCHAIN: ALERTA CRITICO device=" + a.device + " temp=" + a.temperature);
            }

            try {
                reguladorRelay.updateExecutionCost(deviceAddress, true, gasUsado).send();
            } catch (Exception e) {
                System.err.println("Relayer: falha ao atualizar custo na sidechain: " + e.getMessage());
            }

            return alertaCritico;

        } catch (Exception e) {
            System.err.println("Relayer: falha ao encaminhar para a mainchain: " + e.getMessage());
            return false;
        }
    }

    public void iniciarSincronizacao() {
        System.out.println("\n=== Monitor do RELAYER (SIDE CHAIN -> MAIN CHAIN) a cada " + intervaloSincronizacao + "s ===\n");

        schedulerSincronizacao.scheduleAtFixedRate(() -> {
            try {
                System.out.println("\n>>> STATUS RELAYER <<<");
                System.out.println("Transacoes na sidechain: " + contadorTransacoes);
                System.out.println("Operacoes encaminhadas a mainchain: " + contadorRelays);
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
        System.out.println("Operações encaminhadas (Main Chain): " + contadorRelays);
        System.out.println("Alertas críticos (Main Chain): " + contadorCriticos);
        System.out.println("Dispositivos Ativos: " + dispositivos.size());
        System.out.println("=".repeat(60) + "\n");

        exibirRelatorioMetricas();
    }

    public void exibirRelatorioMetricas() { metricas.imprimirRelatorio(); }
}
