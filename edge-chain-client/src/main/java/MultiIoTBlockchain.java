import org.web3j.crypto.Credentials;

public class MultiIoTBlockchain {
    public static void main(String[] args) {
        String mainChainRpcUrl = "http://127.0.0.1:7545";    // Main chain
        String sideChainRpcUrl = "http://127.0.0.1:8545";    // Side chain
        
        String mainChainContratoAddr = "0x194F10d43D96320B100f5183fab2Eb991364Dc96"; // EdgechainMain contract
        String sideChainContratoAddr = "0x96fBc62fE5A67Db7Dea4Bb9e3E90AC92F7010234"; // EdgechainRegulator contract
        
        long intervaloSincronizacao = 20;      // 20s entre synchronizações com main chain
        long duracao = 300;              // Executar por 60s

        Credentials credenciaiMainchain = Credentials.create("0x6d92d14af23dd28a110b32464e0d9fa50a4d1fe7dc763bb5bc68ffb457bb006b");
        
        // Iniciar gerenciador
        GerenciadorDispositivos gerenciador = new GerenciadorDispositivos(
            sideChainRpcUrl, 
            mainChainRpcUrl,
            sideChainContratoAddr,
            mainChainContratoAddr,
            intervaloSincronizacao,
            credenciaiMainchain
        );
        // Financia o REGULADOR (sidechain) ANTES de iniciar: agora ele paga a recompensa
        // via transfer (paridade com a singlechain), e o payout reverteria sem saldo.
        gerenciador.financiarContrato();

        gerenciador.iniciarDispositivos();
        gerenciador.iniciarSincronizacao();
        
        // Monitorar durante X segundos
        gerenciador.monitorarDuracao(duracao);
        gerenciador.exibirEstatisticas();
    }
}
