import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

public class GerenciadorDispositivos {
    private ExecutorService executor;
    private List<DispositivoIoT> dispositivos;

    // Dual Chain
    private ScheduledExecutorService schedulerSincronizacao;
    private Web3j sideChainWeb3j;      // Para dispositivos
    private Web3j mainChainWeb3j;      // Para bridge/anchor
    private String sideChainContratoAddr;
    private String mainChainContratoAddr;
    private long intervaloOperacoes;
    private long intervaloSincronizacao;
    private Bridge bridgeContract;
    private Credentials credenciaisBridge;

    
    // Estatísticas de batching
    private volatile int contadorTransacoes = 0;
    private volatile int contadorBatches = 0;

    // Preenchimento das chaves privadas de acordo com o fornecimento da rede blockchain ganache local
    private static final String[] PRIVATE_KEYS = {
        "0xedaa1c3aa2507c0056058b08694ed82ca8ec0ef8ab949ccfbabb5bd27da73f7e", // Dispositivo 0
        "0xf31c6a902cc0d227ef71bfd6a4b5c0958e30525b90770a556cec18f9686763e2", // Dispositivo 1
        "0x308be3c4f07c2a93dadc856af89ca4c52c3758b2066239ff79c310d13bdfb90a", // Dispositivo 2
        "0xd29ac3e4974488d36c16a9ab2e501bef2ecffcfdb70afdb27a79e39534a0ee9e", // Dispositivo 3
        "0x155bfa1882c97927ee3e7b2feb9e4169ce1b53443e6aee7000f47798a2fe84ec", // Dispositivo 4
    };

    public GerenciadorDispositivos(String sideChainRpcUrl, String mainChainRpcUrl, 
                                   String sideChainContratoAddr, String mainChainContratoAddr,
                                   long intervaloOperacoes, long intervaloSincronizacao, Credentials credenciaisBridge) {
        
        // Inicializar conexões com ambas as chains
        this.sideChainWeb3j = Web3j.build(new HttpService(sideChainRpcUrl));
        this.mainChainWeb3j = Web3j.build(new HttpService(mainChainRpcUrl));
        
        this.sideChainContratoAddr = sideChainContratoAddr;
        this.mainChainContratoAddr = mainChainContratoAddr;
        this.executor = Executors.newFixedThreadPool(5);
        this.schedulerSincronizacao = Executors.newScheduledThreadPool(1);
        this.dispositivos = new ArrayList<>();
        this.intervaloOperacoes = intervaloOperacoes;
        this.intervaloSincronizacao = intervaloSincronizacao;
        this.credenciaisBridge = credenciaisBridge;

        verificarConexoes();

        this.bridgeContract = Bridge.load(
            mainChainContratoAddr, 
            mainChainWeb3j, 
            credenciaisBridge,
            BigInteger.valueOf(20_000_000_000L),
            BigInteger.valueOf(500_000)
        );
    }

    private void verificarConexoes() {
        try {
            String sideChainVersion = sideChainWeb3j.web3ClientVersion().send().getWeb3ClientVersion();
            String mainChainVersion = mainChainWeb3j.web3ClientVersion().send().getWeb3ClientVersion();

            System.out.println("Side Chain conectada: " + sideChainVersion);
            System.out.println("Main Chain conectada: " + mainChainVersion);
        } catch (Exception e) {
            System.err.println("Erro na conexão: " + e.getMessage());
            System.exit(1);
        }
    }

    public void iniciarDispositivos() {
        System.out.println("\n=== Iniciando 5 Dispositivos IoT ===\n");
        
        for (int i = 0; i < 5; i++) {
            try {
                Credentials credenciais = Credentials.create(PRIVATE_KEYS[i]);
                DispositivoIoT dispositivo = new DispositivoIoT(i, sideChainWeb3j, credenciais, sideChainContratoAddr, intervaloOperacoes, this);
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

    public void iniciarSincronizacao() {
        System.out.println("\n=== Iniciando Sincronização (SIDE CHAIN → MAIN CHAIN) a cada " + intervaloSincronizacao + "s ===\n");
        
        schedulerSincronizacao.scheduleAtFixedRate(() -> {
            try {
                sincronizarComMainChain();
            } catch (Exception e) {
                System.err.println("✗ Erro na sincronização: " + e.getMessage());
            }
        }, intervaloSincronizacao, intervaloSincronizacao, TimeUnit.SECONDS);
    }

    private void sincronizarComMainChain() throws Exception {
        // Gerar hash do estado atual da side chain
        String stateHash = gerarHashEstadoSideChain();
        
        System.out.println("\n>>> SINCRONIZAÇÃO INICIADA <<<");
        System.out.println("Sincronizando side chain -> main chain");
        System.out.println("State Hash: " + stateHash.substring(0, 16) + "...");
        System.out.println("Transações pendentes: " + contadorTransacoes);
        
        try {
            // Obter altura do bloco da side chain
            long blockHeight = sideChainWeb3j.ethBlockNumber().send().getBlockNumber().longValue();
           
            byte[] stateHashBytes = Numeric.hexStringToByteArray(stateHash);
            if (stateHashBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(stateHashBytes, 0, padded, 32 - stateHashBytes.length, stateHashBytes.length);
                stateHashBytes = padded;
            }

            long transacoes = Math.max(1, contadorTransacoes);

            // Submeter batch no bridge
            TransactionReceipt receipt = bridgeContract.submeterBatch(
                stateHashBytes,
                BigInteger.valueOf(blockHeight),
                BigInteger.valueOf(transacoes)
            ).send();
            
            System.out.println("  Batch ancorado com sucesso!");
            System.out.println("  TX Hash: " + receipt.getTransactionHash().substring(0, 10) + "...");
            System.out.println("  Block: " + receipt.getBlockNumber());
            System.out.println("  Gas Used: " + receipt.getGasUsed());
            
        } catch (Exception e) {
            System.err.println("Erro ao submeter batch: " + e.getMessage());
        }
        
        contadorBatches++;
        contadorTransacoes = 0;
        
        System.out.println(">>> SINCRONIZAÇÃO CONCLUÍDA <<<");
        System.out.println("Total de sincronizações: " + contadorBatches + "\n");
    }

    private String gerarHashEstadoSideChain() throws Exception {
        try {
            // Obter número do bloco atual da side chain
            BigInteger blockNumber = sideChainWeb3j.ethBlockNumber().send().getBlockNumber();
            String blockHash = sideChainWeb3j.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameter.valueOf(blockNumber),
                false
            ).send().getBlock().getHash();
            
            return blockHash != null ? blockHash : "0x" + System.currentTimeMillis();
        } catch (Exception e) {
            return "0x" + System.nanoTime();
        }
    }

    public void registrarTransacao() {
        contadorTransacoes++;
    }

    public void exibirEstatisticas() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ESTATÍSTICAS DO GERENCIADOR");
        System.out.println("=".repeat(60));
        System.out.println("Total de Transações (Side Chain): " + contadorTransacoes);
        System.out.println("Total de Anchors (Main Chain): " + contadorBatches);
        System.out.println("Dispositivos Ativos: " + dispositivos.size());
        System.out.println("=".repeat(60) + "\n");
    }
}
