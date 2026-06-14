import org.web3j.crypto.Credentials;

public class MultiIoTBlockchain {
    public static void main(String[] args) {
        // Configuração

        // URLs das duas chains
        String sideChainRpcUrl = "http://127.0.0.1:8545";    // Side chain
        String mainChainRpcUrl = "http://127.0.0.1:7545";    // Main chain
        
        String sideChainContratoAddr = "0x50f14485A899331a3449CD3e7d9FD6ea19A63712"; // EdgeChain contract
        String mainChainContratoAddr = "0x0c87F874d740D5791DB2297C1E83Ea40F4072164"; // Bridge contract
        
        long intervaloOperacoes = 2000; // 2s entre operações dos dispositivos
        long intervaloSincronizacao = 20;      // 20s entre synchronizações com main chain
        long duracao = 60;              // Executar por 60s

        Credentials credenciaiBridge = Credentials.create("0x417d4fc5b9152250cce14bad2ff8019fde5ffe3f943ad99117b2a7372602af35");
        
        // Iniciar gerenciador
        GerenciadorDispositivos gerenciador = new GerenciadorDispositivos(
            sideChainRpcUrl, 
            mainChainRpcUrl,
            sideChainContratoAddr,
            mainChainContratoAddr,
            intervaloOperacoes,
            intervaloSincronizacao,
            credenciaiBridge
        );
        gerenciador.iniciarDispositivos();
        gerenciador.iniciarSincronizacao();
        
        // Monitorar durante X segundos
        gerenciador.monitorarDuracao(duracao);
        gerenciador.exibirEstatisticas();
    }
}
