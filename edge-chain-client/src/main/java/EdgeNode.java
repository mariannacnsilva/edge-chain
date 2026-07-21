import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * ============================================================================
 * EdgeNode  ---  CAMADA DE COMPUTACAO EM BORDA (SINGLECHAIN)
 * ============================================================================
 *
 * Nova camada intermediaria inserida ENTRE os dispositivos IoT e o contrato:
 *
 *      Dispositivos IoT  ->  Edge Node (Java)  ->  EdgeChain.sol  ->  Ganache
 *
 * O Edge Node NAO substitui o contrato: toda a logica de cadastro, reputacao,
 * penalidade e recompensa continua sendo executada on-chain pelo EdgeChain
 * (funcao any_operation, reaproveitada SEM alteracao). O Edge Node apenas faz
 * PROCESSAMENTO LOCAL antes da blockchain:
 *
 *   - recebe leituras dos dispositivos (chamada em memoria, sem tx);
 *   - mantem um buffer temporario por dispositivo (DeviceBuffer);
 *   - agrega leituras (min/max/media/contador/janela);
 *   - detecta eventos criticos (AlertDetector);
 *   - encaminha apenas RESUMOS relevantes ao contrato (BatchManager decide quando).
 *
 * REDUCAO DE CHAMADAS AO CONTRATO
 * Na Singlechain original cada leitura gera 1 transacao any_operation. Aqui, N
 * leituras de um dispositivo sao consolidadas em 1 unica transacao any_operation,
 * enviada com as CREDENCIAIS DO PROPRIO DISPOSITIVO (msg.sender preservado), de
 * modo que o contrato continua identificando/pontuando cada dispositivo
 * individualmente. Assim caem: numero de transacoes, gas total, chamadas RPC e a
 * latencia media por leitura.
 * ============================================================================
 */
public class EdgeNode {

    private final Web3j web3j;
    private final String contratoAddr;

    private final BatchManager  batchManager;
    private final AlertDetector alertDetector;
    private final MetricasExecucao metricas;

    // Estado por dispositivo (id -> ...). Um contrato EdgeChain por dispositivo,
    // carregado com as credenciais do dispositivo para preservar o msg.sender.
    private final Map<Integer, DeviceBuffer> buffers     = new ConcurrentHashMap<>();
    private final Map<Integer, EdgeChain>    contratos   = new ConcurrentHashMap<>();
    private final Map<Integer, String>       enderecos   = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean>      cadastrado  = new ConcurrentHashMap<>();

    // Codigos de retorno de any_operation (definidos pelo contrato EdgeChain).
    private static final int STATUS_VERSAO_REJEITADA = 2;
    private static final int STATUS_BLOQUEADO = 3;

    public EdgeNode(Web3j web3j, String contratoAddr,
                    BatchManager batchManager, AlertDetector alertDetector,
                    MetricasExecucao metricas) {
        this.web3j         = web3j;
        this.contratoAddr  = contratoAddr;
        this.batchManager  = batchManager;
        this.alertDetector = alertDetector;
        this.metricas      = metricas;
    }

    /**
     * Registra um dispositivo no Edge Node. Cria o buffer e carrega o contrato
     * EdgeChain com as credenciais do dispositivo (reaproveita o wrapper Web3j
     * existente, sem gerar novo contrato).
     */
    public void registrarDispositivo(int id, Credentials credenciais) {
        buffers.put(id, new DeviceBuffer());
        enderecos.put(id, credenciais.getAddress());
        cadastrado.put(id, Boolean.FALSE);
        contratos.put(id, EdgeChain.load(contratoAddr, web3j, credenciais,
                BigInteger.valueOf(20_000_000_000L),
                BigInteger.valueOf(500_000)));
    }

    /**
     * PASSOS 1/2/3 do novo fluxo: recebe a leitura de um dispositivo, armazena no
     * buffer em memoria e agrega. NENHUMA transacao e feita aqui, a menos que o
     * BatchManager decida que o batch deve ser enviado (lote cheio, tempo ou
     * evento critico).
     */
    public void receberLeitura(int id, BigInteger temperatura, BigInteger timestamp) {
        metricas.registrarLeituraRecebidaEdge();

        DeviceBuffer buffer = buffers.get(id);
        if (buffer == null) return; // dispositivo nao registrado

        boolean critico = alertDetector.isCritico(temperatura);

        DeviceBuffer.Resumo resumo = null;
        String motivo = null;

        // Regiao critica curta: agrega e decide. O envio (I/O de rede) fica FORA
        // do lock para nao bloquear novas leituras deste dispositivo.
        synchronized (buffer) {
            buffer.adicionar(temperatura, timestamp, critico);
            long agora = System.currentTimeMillis();
            if (batchManager.deveEnviar(buffer, agora)) {
                motivo = batchManager.motivoEnvio(buffer, agora);
                resumo = buffer.consolidarEReiniciar();
            }
        }

        if (resumo != null) {
            enviarBatch(id, resumo, motivo);
        }
    }

    /**
     * Gatilho por TEMPO / encerramento: consolida o buffer de um dispositivo.
     * @param forcar quando true, envia qualquer leitura pendente (usado no
     *               shutdown para nao perder o residuo); quando false, respeita
     *               a politica do BatchManager (tempo de janela).
     */
    public void flushDispositivo(int id, boolean forcar) {
        DeviceBuffer buffer = buffers.get(id);
        if (buffer == null) return;

        DeviceBuffer.Resumo resumo = null;
        String motivo = null;

        synchronized (buffer) {
            long agora = System.currentTimeMillis();
            boolean enviar = forcar ? buffer.temLeituras() : batchManager.deveEnviar(buffer, agora);
            if (enviar) {
                motivo = forcar ? "SHUTDOWN" : batchManager.motivoEnvio(buffer, agora);
                resumo = buffer.consolidarEReiniciar();
            }
        }

        if (resumo != null) {
            enviarBatch(id, resumo, motivo);
        }
    }

    /** Consolida todos os dispositivos (scheduler por tempo e encerramento). */
    public void flushTodos(boolean forcar) {
        for (Integer id : buffers.keySet()) {
            flushDispositivo(id, forcar);
        }
    }

    /**
     * PASSO 5 do novo fluxo: encaminha UM resumo consolidado ao contrato,
     * reaproveitando any_operation (mesma chamada da Singlechain original). O
     * contrato executa seus 6 passos internos (cadastro, comportamento, gas,
     * recompensa) exatamente como antes — apenas com MENOS transacoes.
     *
     * O resumo completo (min/media/max/janela/critico) e processado na borda e
     * registrado em log e nas metricas; o contrato mantem a ABI intacta.
     */
    private void enviarBatch(int id, DeviceBuffer.Resumo r, String motivo) {
        String endereco = enderecos.get(id);
        EdgeChain contrato = contratos.get(id);
        if (contrato == null) return;

        long inicio = System.currentTimeMillis();
        try {
            // Observabilidade (PASSO 2/4): status que o contrato retornaria.
            // Feito 1x por BATCH (nao por leitura) -> forte reducao de RPC.
            int status = consultarStatusOperacao(endereco);
            if (Boolean.FALSE.equals(cadastrado.get(id))) {
                cadastrado.put(id, Boolean.TRUE);
                System.out.println("[EdgeNode] Dispositivo-" + id + " cadastrado no contrato (" + endereco + ")");
            }
            if (status == STATUS_BLOQUEADO) {
                System.out.println("[EdgeNode] Dispositivo-" + id + " BLOQUEADO pelo contrato (penalidade alta).");
            } else if (status == STATUS_VERSAO_REJEITADA) {
                System.out.println("[EdgeNode] Dispositivo-" + id + " operacao rejeitada (versao incompativel).");
            }

            // --- 1 TRANSACAO por BATCH (em vez de 1 por leitura) ---
            TransactionReceipt receipt = contrato.any_operation(BigInteger.ZERO).send();
            BigInteger gas = receipt.getGasUsed();
            long latenciaMs = System.currentTimeMillis() - inicio;

            metricas.registrarBatchEdge(endereco, gas, r.leituras, latenciaMs);

            System.out.println(">>> BATCH -> EdgeChain device-" + id
                    + " | motivo=" + motivo
                    + " leituras=" + r.leituras
                    + " min=" + r.min + " max=" + r.max + " media=" + r.media
                    + " ultima=" + r.ultima
                    + " tsIni=" + r.tsInicial + " tsFim=" + r.tsFinal
                    + " critico=" + r.critico
                    + " (gas: " + gas + " | latencia: " + latenciaMs + " ms)");

            if (r.critico) {
                System.out.println(">>> ALERTA CRITICO (batch) device-" + id
                        + " tempMax=" + r.max + " leituras=" + r.leituras);
            }

            // PASSO 6: observa a recompensa/saldo do dispositivo (1x por batch).
            try {
                BigInteger saldo = contrato.getUserBalance().send();
                System.out.println("[EdgeNode] Dispositivo-" + id + " saldo/recompensa: " + saldo + " wei");
            } catch (Exception ignore) {
                // saldo indisponivel nesta iteracao
            }
        } catch (Exception e) {
            System.out.println("[EdgeNode] Erro ao enviar batch do Dispositivo-" + id + " a EdgeChain: " + e.getMessage());
        }
    }

    /**
     * eth_call que simula any_operation e retorna o codigo de status sem gastar
     * gas (reaproveita a mesma tecnica de DispositivoIoTSingle.consultarStatusOperacao).
     */
    private int consultarStatusOperacao(String endereco) {
        try {
            Function function = new Function(
                    EdgeChain.FUNC_ANY_OPERATION,
                    Collections.<Type>singletonList(new Uint256(BigInteger.ZERO)),
                    Collections.<TypeReference<?>>singletonList(new TypeReference<Uint256>() {}));
            String data = FunctionEncoder.encode(function);

            EthCall resp = web3j.ethCall(
                    Transaction.createEthCallTransaction(endereco, contratoAddr, data),
                    DefaultBlockParameterName.LATEST).send();

            if (resp.hasError()) return -1;
            String value = resp.getValue();
            if (value == null || value.equals("0x") || value.isEmpty()) return -1;

            List<Type> out = FunctionReturnDecoder.decode(value, function.getOutputParameters());
            if (out.isEmpty()) return -1;
            return ((BigInteger) out.get(0).getValue()).intValue();
        } catch (Exception e) {
            return -1;
        }
    }
}
