import org.web3j.crypto.Credentials;

public class MultiIoTBlockchain {
    public static void main(String[] args) {
        String mainChainRpcUrl = "http://127.0.0.1:7545";    // Main chain
        String sideChainRpcUrl = "http://127.0.0.1:8545";    // Side chain
        
        String mainChainContratoAddr = "0x28d46cB0d6870Ec385dc8D99E52514D861717370"; // EdgechainMain contract
        String sideChainContratoAddr = "0x359146E53e8B20E434937713d46564b408fC6762"; // EdgechainRegulator contract
        
        long intervaloSincronizacao = 20;      // 20s entre synchronizações com main chain
        long duracao = 300;              // Executar por 60s

        Credentials credenciaiMainchain = Credentials.create("0x4195f3e1b109d46754f60c81bdb62c884074016b1f37065103b376310df8b527");
        
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

        // Gatilhos de AGREGACAO/FILTRAGEM (facilmente configuraveis):
        //   loteMin            = 50 leituras consolidadas por envio a mainchain;
        //   deltaSignificativo = 5 C de variacao dispara envio;
        //   limiteCritico      = 45 C (alinhado a EdgechainMain) dispara envio imediato.
        // O gatilho por TEMPO reutiliza intervaloSincronizacao (acima).
        gerenciador.configurarBatch(50, 5, 45);

        gerenciador.iniciarDispositivos();
        gerenciador.iniciarSincronizacao();
        
        // Monitorar durante X segundos
        gerenciador.monitorarDuracao(duracao);
        gerenciador.exibirEstatisticas();
    }
}
