/**
 * ============================================================================
 * EdgeIoTBlockchain  ---  RUNNER DA ARQUITETURA COM EDGE COMPUTING (SINGLECHAIN)
 * ============================================================================
 *
 * Ponto de entrada equivalente ao SingleIoTBlockchain, porem com a camada de
 * Edge Computing inserida entre os dispositivos e o contrato:
 *
 *      Dispositivos IoT  ->  Edge Node (Java)  ->  EdgeChain.sol  ->  Ganache
 *
 * Usa os MESMOS defaults do SingleIoTBlockchain (RPC, endereco do contrato e
 * duracao) para permitir uma comparacao direta com a Singlechain original.
 * ============================================================================
 */
public class EdgeIoTBlockchain {
    public static void main(String[] args) {
        // Configuracao (mesmos defaults do SingleIoTBlockchain para comparacao justa).
        String rpcUrl = "http://127.0.0.1:9545";
        String contratoAddr = "0x9276e15C13Caa5790DAE30863d386610141d1711"; // EdgeChain contract addr
        long duracao = 300; // Executar por 300s

        // Politica de agregacao do Edge Node (gatilhos (a) e (b) da especificacao).
        int  loteMin        = 50; // (a) envia ao contrato a cada 50 leituras
        long janelaSegundos = 60; // (b) ou a cada 60 segundos (o que ocorrer primeiro)
        // (c) temperatura critica -> flush imediato (tratado pelo AlertDetector).

        GerenciadorDispositivosEdge gerenciador =
                new GerenciadorDispositivosEdge(rpcUrl, contratoAddr, loteMin, janelaSegundos);

        gerenciador.financiarContrato();
        gerenciador.financiarDispositivos();
        gerenciador.iniciarDispositivos();

        // Monitorar durante X segundos.
        gerenciador.monitorarDuracao(duracao);
        gerenciador.exibirEstatisticas();
    }
}
