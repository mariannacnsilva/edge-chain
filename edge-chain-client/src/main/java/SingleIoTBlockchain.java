import org.web3j.crypto.Credentials;

public class SingleIoTBlockchain {
    public static void main(String[] args) {
        // Configuração (mesmos defaults do MultiIoTBlockchain para comparacao justa)

        String rpcUrl = "http://127.0.0.1:9545";
        String contratoAddr = "0xf92700A6a609d18F6D0fB96c56f86a01e49fd325"; // EdgeChain contract addr
        long duracao = 300; // Executar por 300s

        // Iniciar gerenciador (singlechain)
        GerenciadorDispositivosSingle gerenciador = new GerenciadorDispositivosSingle( rpcUrl, contratoAddr );
        gerenciador.financiarContrato();
        gerenciador.financiarDispositivos();
        gerenciador.iniciarDispositivos();

        // Monitorar durante X segundos
        gerenciador.monitorarDuracao(duracao);
        gerenciador.exibirEstatisticas();
    }
}
