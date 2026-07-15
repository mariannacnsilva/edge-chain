import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

public class GerenciadorDispositivosSingle {
    private ExecutorService executor;
    private List<DispositivoIoTSingle> dispositivos;

    private Web3j web3j;                 // chain unica
    private String contratoAddr;        // endereco do EdgeChain

    // Estatísticas
    private volatile int contadorTransacoes = 0;   // tx enviadas a singlechain (para TPS)

    // Coletor de metricas (o MESMO usado pela dualchain, para dados comparaveis)
    private final MetricasExecucao metricas = new MetricasExecucao();

    /** Acesso ao coletor de metricas (usado pelos dispositivos e pelo relatorio final). */
    public MetricasExecucao getMetricas() {
        return metricas;
    }

    private static final String[] PRIVATE_KEYS = {
        "0x01f7cf8fe564e02eb6f467a16a78203fe483c268a6f24e9563e1c658f21348f3", // Dispositivo 0
        "0x04a39e26091fbc8facda4897455a4d4d024b7d9449ad1dcd1241708a6b72dcab", // Dispositivo 1
        "0xac351f7db2e0589dfd0123889006a7d48686ca83c26666f95a6c90909534bd76", // Dispositivo 2
        "0x3d910288629335bb5f943a329909055942f967553f1ab0958641c9442214f8d6", // Dispositivo 3
        "0x94a6365c3e2278207cf6540d56d93c45793faa4a1abc7ad2e21b642c537d2c2d", // Dispositivo 4
    };

    public GerenciadorDispositivosSingle(String rpcUrl, String contratoAddr) {
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.contratoAddr = contratoAddr;
        this.executor = Executors.newFixedThreadPool(5);
        this.dispositivos = new ArrayList<>();

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
            System.err.println("[ERRO] Falha ao financiar o contrato (o payout vai reverter e a single vai parar cedo): " + e.getMessage());
        }
    }

    // Valor enviado a cada dispositivo para garantir gas de operacao durante a execucao.
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
        System.out.println("\n=== Iniciando 5 Dispositivos IoT (SINGLECHAIN) ===\n");

        for (int i = 0; i < 5; i++) {
            try {
                Credentials credenciais = Credentials.create(PRIVATE_KEYS[i]);
                DispositivoIoTSingle dispositivo = new DispositivoIoTSingle(i, web3j, credenciais, contratoAddr, this);
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

        dispositivos.forEach(DispositivoIoTSingle::stop);
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
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

    public void registrarTransacao() {
        contadorTransacoes++;
    }

    public void exibirEstatisticas() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ESTATÍSTICAS DO GERENCIADOR (SINGLECHAIN)");
        System.out.println("=".repeat(60));
        System.out.println("Total de Transações (Single Chain): " + contadorTransacoes);
        System.out.println("Dispositivos Ativos: " + dispositivos.size());
        System.out.println("=".repeat(60) + "\n");

        metricas.imprimirRelatorioSinglechain();
    }
}
