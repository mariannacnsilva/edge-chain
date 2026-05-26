public class MultiIoTBlockchain {
    public static void main(String[] args) {
        // Configuração
        String rpcUrl = "HTTP://127.0.0.1:7545";
        String contratoAddr = "0xE5dE808CDb89656AcC3aeCC3433310aE1a897D4A";
        long intervaloOperacoes = 2000; // 2s entre operações
        long duracao = 60; // Executar por 60s
        
        // Iniciar gerenciador
        GerenciadorDispositivos gerenciador = new GerenciadorDispositivos(rpcUrl, contratoAddr, intervaloOperacoes);
        gerenciador.iniciarDispositivos();
        
        // Monitorar durante X segundos
        gerenciador.monitorarDuracao(duracao);
    }
}
