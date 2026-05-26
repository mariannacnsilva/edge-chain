import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

public class GerenciadorDispositivos {
    private ExecutorService executor;
    private List<DispositivoIoT> dispositivos;
    private Web3j web3j;
    private String contratoAddr;
    private long intervaloOperacoes;

    // Preenchimento das chaves privadas de acordo com o fornecimento da rede blockchain ganache local
    private static final String[] PRIVATE_KEYS = {
        "0x470b1384bec70a2aed460c8b54556e95ec8d712df392fc0af1856cb367593e4d", // Dispositivo 0
        "0xb0dc9f42dcdf760eb31569ffb3055e65a4493d74e82990a1fa4732fb20a26260", // Dispositivo 1
        "0x4ae78164834939a5c5f7bfd84fca298d4991c82dbea7a165584ce037fb970719", // Dispositivo 2
        "0x57c59fd5db0b69e8f3cd0c5ba91cde9c868fa5907fc2ef5ad7e350be5ef3e12f", // Dispositivo 3
        "0xadc2106dd9a6ef8670ad12d225bc8a037a09661d9e5ff406cf90fa13f50afc47", // Dispositivo 4
    };

    public GerenciadorDispositivos(String rpcUrl, String contratoAddr, long intervaloOperacoes) {
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.contratoAddr = contratoAddr;
        this.executor = Executors.newFixedThreadPool(5);
        this.dispositivos = new ArrayList<>();
        this.intervaloOperacoes = intervaloOperacoes;

        verificarConexao();
    }

    private void verificarConexao() {
        try {
            String version = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            System.out.println("Conectado ao blockchain: " + version);
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
                DispositivoIoT dispositivo = new DispositivoIoT(i, web3j, credenciais, contratoAddr, intervaloOperacoes);
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
}
