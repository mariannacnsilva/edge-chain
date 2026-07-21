import org.web3j.crypto.Credentials;

/**
 * ============================================================================
 * DualEdgeIoTBlockchain  ---  RUNNER DA DUALCHAIN COM EDGE NODE (BATCH + HASH)
 * ============================================================================
 *
 * Ponto de entrada equivalente ao MultiIoTBlockchain, porem com o Edge Node
 * operando em LOTE (agregacao + hash SHA-256) tanto para a sidechain quanto para
 * a mainchain:
 *
 *      IoT Devices -> Edge Node -> Sidechain -> Mainchain
 *
 * Usa os MESMOS defaults do MultiIoTBlockchain (RPCs, enderecos e relayer) para
 * comparacao direta com a dualchain original. ATENCAO: apos recompilar/reimplantar
 * os contratos (truffle migrate), atualize os enderecos abaixo.
 * ============================================================================
 */
public class DualEdgeIoTBlockchain {
    public static void main(String[] args) {
        String mainChainRpcUrl = "http://127.0.0.1:7545"; // Main chain
        String sideChainRpcUrl = "http://127.0.0.1:8545"; // Side chain

        String mainChainContratoAddr = "0x3646D3b303E8A9ed6F25ddf0bcbDBd78F75dc0f6"; // EdgechainMain
        String sideChainContratoAddr = "0xF88452ba057E19953E7A611976f4A6a2f5ABCC08"; // EdgechainRegulator

        long duracao = 300;         // Executar por 300s

        // Politica de agregacao do Edge Node (mesmos gatilhos da SingleChain edge):
        int  loteMin        = 50;   // (a) envia o lote a cada 50 leituras
        long janelaSegundos = 60;   // (b) ou a cada 60 segundos (o que ocorrer primeiro)
        // (c) temperatura critica -> flush imediato (tratado pelo AlertDetector).

        Credentials credenciaisRelayer = Credentials.create(
            "0x282638d938afae6cc0c0dfcaa6ce1c69531aa62fc1787c4c0d98ac9902a508d9");

        GerenciadorDispositivosDualEdge gerenciador = new GerenciadorDispositivosDualEdge(
            sideChainRpcUrl,
            mainChainRpcUrl,
            sideChainContratoAddr,
            mainChainContratoAddr,
            janelaSegundos,
            credenciaisRelayer,
            loteMin
        );

        // Financia o REGULADOR (sidechain), como na dualchain original.
        gerenciador.financiarContrato();

        gerenciador.iniciarDispositivos();

        // Monitorar durante X segundos.
        gerenciador.monitorarDuracao(duracao);
        gerenciador.exibirEstatisticas();
    }
}
