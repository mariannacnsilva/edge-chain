import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * ============================================================================
 * EdgeNodeDual  ---  CAMADA DE COMPUTACAO EM BORDA (DUALCHAIN, EM LOTE + HASH)
 * ============================================================================
 *
 * Aplica na DUALCHAIN a mesma estrategia ja usada na SingleChain otimizada, mas
 * preservando as DUAS blockchains:
 *
 *      IoT Devices -> Edge Node -> Sidechain -> Mainchain
 *
 * O Edge Node:
 *   1. recebe TODAS as leituras dos sensores (chamada local, sem transacao);
 *   2. armazena as leituras COMPLETAS em memoria (Batch.readings) para auditoria;
 *   3. agrupa as leituras em lotes (BatchManager decide quando fechar);
 *   4. calcula um hash SHA-256 do lote (Batch.fechar);
 *   5. envia a SIDECHAIN apenas o RESUMO do lote (hash + metadados) via
 *      registerBatch -- NAO envia cada leitura individualmente;
 *   6. se a sidechain APROVAR, encaminha a MAINCHAIN somente o hash + metadados
 *      via registerBatchHash (nunca as leituras).
 *
 * Toda a logica de reputacao, penalidade, recompensa, bloqueio e validacao
 * continua sendo executada pelos contratos (agora por LOTE, nao por leitura).
 * As chamadas a sidechain usam as CREDENCIAIS DO PROPRIO DISPOSITIVO (msg.sender
 * preservado), de modo que o contrato continua pontuando cada dispositivo.
 * ============================================================================
 */
public class EdgeNodeDual {

    private final Web3j sideChainWeb3j;
    private final String sideChainContratoAddr;

    private final EdgechainMain mainContract;       // relayer -> registerBatchHash (mainchain)
    private final EdgechainRegulator reguladorRelay; // relayer -> updateExecutionCost (sidechain)

    private final BatchManager  batchManager;
    private final AlertDetector alertDetector;
    private final MetricasExecucao metricas;

    private static final String DEVICE_TYPE = "temperature";

    // Estado por dispositivo.
    private final Map<Integer, EdgechainRegulator> reguladorPorDispositivo = new ConcurrentHashMap<>();
    private final Map<Integer, String>  enderecos  = new ConcurrentHashMap<>();
    private final Map<Integer, String>  deviceIds  = new ConcurrentHashMap<>();
    private final Map<Integer, Batch>   batchAtual = new ConcurrentHashMap<>();
    private final Map<Integer, Object>  locks      = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> bloqueado  = new ConcurrentHashMap<>();

    private final AtomicLong batchIdSeq = new AtomicLong(0);

    // Estatisticas locais do Edge Node.
    private volatile int contadorBatches = 0;
    private volatile int contadorAprovados = 0;
    private volatile int contadorCriticos = 0;
    private volatile int contadorBloqueios = 0;

    public EdgeNodeDual(Web3j sideChainWeb3j, String sideChainContratoAddr,
                        EdgechainMain mainContract, EdgechainRegulator reguladorRelay,
                        BatchManager batchManager, AlertDetector alertDetector,
                        MetricasExecucao metricas) {
        this.sideChainWeb3j = sideChainWeb3j;
        this.sideChainContratoAddr = sideChainContratoAddr;
        this.mainContract = mainContract;
        this.reguladorRelay = reguladorRelay;
        this.batchManager = batchManager;
        this.alertDetector = alertDetector;
        this.metricas = metricas;
    }

    /**
     * Registra um dispositivo no Edge Node: cria o contrato regulador da sidechain
     * com as credenciais do dispositivo (preserva o msg.sender) e o primeiro lote.
     */
    public void registrarDispositivo(int id, String deviceId, Credentials credenciais) {
        reguladorPorDispositivo.put(id, EdgechainRegulator.load(
                sideChainContratoAddr, sideChainWeb3j, credenciais,
                BigInteger.valueOf(20_000_000_000L),
                BigInteger.valueOf(500_000)));
        enderecos.put(id, credenciais.getAddress());
        deviceIds.put(id, deviceId);
        locks.put(id, new Object());
        bloqueado.put(id, Boolean.FALSE);
        batchAtual.put(id, new Batch(batchIdSeq.incrementAndGet(), credenciais.getAddress()));
    }

    /**
     * Recebe UMA leitura de um dispositivo. Armazena em memoria e agrega; so fecha
     * e envia o lote quando o BatchManager decide (lote cheio, tempo ou critico).
     * NENHUMA transacao ocorre por leitura.
     */
    public void receberLeitura(int id, BigInteger temperatura, BigInteger timestamp) {
        metricas.registrarLeituraRecebidaEdge();

        Object lock = locks.get(id);
        if (lock == null) return; // dispositivo nao registrado

        boolean critico = alertDetector.isCritico(temperatura);

        Batch fechado = null;
        String motivo = null;
        synchronized (lock) {
            Batch b = batchAtual.get(id);
            SensorReading leitura = new SensorReading(deviceIds.get(id), enderecos.get(id), temperatura, timestamp);
            b.addReading(leitura, critico);

            long agora = System.currentTimeMillis();
            if (batchManager.deveEnviar(b.getTotalReadings(), b.isCritico(), b.getInicioJanelaMs(), agora)) {
                motivo = batchManager.motivoEnvio(b.getTotalReadings(), b.isCritico(), b.getInicioJanelaMs(), agora);
                fechado = b;
                // Abre um novo lote para as proximas leituras deste dispositivo.
                batchAtual.put(id, new Batch(batchIdSeq.incrementAndGet(), enderecos.get(id)));
            }
        }

        if (fechado != null) {
            enviarBatch(id, fechado, motivo);
        }
    }

    /**
     * Gatilho por TEMPO / encerramento: fecha o lote pendente de um dispositivo.
     * @param forcar quando true, envia qualquer leitura pendente (shutdown).
     */
    public void flushDispositivo(int id, boolean forcar) {
        Object lock = locks.get(id);
        if (lock == null) return;

        Batch fechado = null;
        String motivo = null;
        synchronized (lock) {
            Batch b = batchAtual.get(id);
            long agora = System.currentTimeMillis();
            boolean enviar = forcar ? !b.isEmpty()
                    : batchManager.deveEnviar(b.getTotalReadings(), b.isCritico(), b.getInicioJanelaMs(), agora);
            if (enviar) {
                motivo = forcar ? "SHUTDOWN"
                        : batchManager.motivoEnvio(b.getTotalReadings(), b.isCritico(), b.getInicioJanelaMs(), agora);
                fechado = b;
                batchAtual.put(id, new Batch(batchIdSeq.incrementAndGet(), enderecos.get(id)));
            }
        }

        if (fechado != null) {
            enviarBatch(id, fechado, motivo);
        }
    }

    /** Consolida todos os dispositivos (scheduler por tempo e encerramento). */
    public void flushTodos(boolean forcar) {
        for (Integer id : locks.keySet()) {
            flushDispositivo(id, forcar);
        }
    }

    /**
     * Fecha o lote (hash SHA-256), envia o RESUMO a SIDECHAIN (registerBatch) e, se
     * aprovado, encaminha o HASH a MAINCHAIN (registerBatchHash). A lista completa
     * de leituras NUNCA sai do Edge Node.
     */
    private void enviarBatch(int id, Batch b, String motivo) {
        String endereco = enderecos.get(id);
        String deviceId = deviceIds.get(id);
        EdgechainRegulator regulador = reguladorPorDispositivo.get(id);
        if (regulador == null) return;

        long inicio = System.currentTimeMillis();
        try {
            // (4) Calcula o hash SHA-256 de TODO o lote (integridade/rastreabilidade).
            byte[] hash = b.fechar();
            BigInteger count = BigInteger.valueOf(b.getTotalReadings());

            // (5) SIDECHAIN: recebe apenas o RESUMO (hash + metadados), nao as leituras.
            TransactionReceipt sideReceipt = regulador.registerBatch(
                    deviceId, DEVICE_TYPE, hash, count,
                    b.getMax(), b.getAvg(), b.getFirstTs(), b.getLastTs()
            ).send();

            BigInteger sideGas = sideReceipt.getGasUsed();
            contadorBatches++;
            metricas.registrarLeituraSidechain(endereco, sideGas); // metrica sidechain (por lote)

            boolean aprovada = lerAprovacao(regulador, sideReceipt);
            boolean bloqueio = !regulador.getDeviceBlockedEvents(sideReceipt).isEmpty();
            if (bloqueio) {
                bloqueado.put(id, Boolean.TRUE);
                contadorBloqueios++;
                System.out.println("[EdgeNodeDual] Dispositivo-" + id + " BLOQUEADO TEMPORARIAMENTE pela sidechain (lote de flood).");
            } else if (aprovada && Boolean.TRUE.equals(bloqueado.get(id))
                    && !regulador.getDeviceUnblockedEvents(sideReceipt).isEmpty()) {
                bloqueado.put(id, Boolean.FALSE);
                System.out.println("[EdgeNodeDual] Dispositivo-" + id + " REABILITADO pela sidechain.");
            }

            System.out.println(">>> BATCH " + b.getBatchLabel() + " -> SIDECHAIN device-" + id
                    + " | motivo=" + motivo
                    + " leituras=" + b.getTotalReadings()
                    + " min=" + b.getMin() + " max=" + b.getMax() + " media=" + b.getAvg()
                    + " tsIni=" + b.getFirstTs() + " tsFim=" + b.getLastTs()
                    + " hash=" + resumoHash(b.getHash())
                    + " aprovado=" + aprovada
                    + " (gas sidechain: " + sideGas + ")");

            BigInteger mainGas = BigInteger.ZERO;
            if (aprovada) {
                contadorAprovados++;
                // (6) MAINCHAIN: registra APENAS o hash + metadados do lote aprovado.
                TransactionReceipt mainReceipt = mainContract.registerBatchHash(
                        endereco,
                        BigInteger.valueOf(b.getBatchId()),
                        hash,
                        BigInteger.valueOf(b.getTimestamp()),
                        b.getMax(),
                        count
                ).send();
                mainGas = mainReceipt.getGasUsed();
                metricas.registrarLeituraMainchain(endereco, mainGas); // metrica mainchain (por lote)

                List<EdgechainMain.CriticalAlertEventResponse> alertas =
                        mainContract.getCriticalAlertEvents(mainReceipt);
                for (EdgechainMain.CriticalAlertEventResponse a : alertas) {
                    contadorCriticos++;
                    System.out.println(">>> MAINCHAIN: ALERTA CRITICO (lote) device=" + a.device
                            + " tempMax=" + a.temperature + " leituras=" + b.getTotalReadings());
                }

                System.out.println(">>> BATCH " + b.getBatchLabel() + " -> MAINCHAIN device-" + id
                        + " | hash registrado=" + resumoHash(b.getHash())
                        + " (gas mainchain: " + mainGas + ")");

                // SIDECHAIN: recebe de volta o custo consolidado (sem transfer).
                try {
                    reguladorRelay.updateExecutionCost(endereco, true, mainGas).send();
                } catch (Exception e) {
                    System.err.println("[EdgeNodeDual] falha ao atualizar custo na sidechain: " + e.getMessage());
                }
            }

            long latencia = System.currentTimeMillis() - inicio;
            // Metrica de agregacao (leituras recebidas x batches, fator, latencia).
            metricas.registrarBatchEdge(endereco, sideGas.add(mainGas), b.getTotalReadings(), latencia);

        } catch (Exception e) {
            System.out.println("[EdgeNodeDual] Erro ao enviar lote do Dispositivo-" + id + ": " + e.getMessage());
        }
    }

    /** Le o evento BatchValidated (ou ReadingValidated) para saber se o lote foi aprovado. */
    private boolean lerAprovacao(EdgechainRegulator regulador, TransactionReceipt receipt) {
        List<EdgechainRegulator.BatchValidatedEventResponse> lotes =
                regulador.getBatchValidatedEvents(receipt);
        if (!lotes.isEmpty()) {
            return lotes.get(lotes.size() - 1).approved;
        }
        // Fallback: ReadingValidated (compatibilidade).
        List<EdgechainRegulator.ReadingValidatedEventResponse> leituras =
                regulador.getReadingValidatedEvents(receipt);
        return !leituras.isEmpty() && leituras.get(leituras.size() - 1).approved;
    }

    private static String resumoHash(String hexHash) {
        if (hexHash == null) return "-";
        return hexHash.length() > 12 ? hexHash.substring(0, 12) + "..." : hexHash;
    }

    // Getters de estatisticas do Edge Node.
    public int getContadorBatches()   { return contadorBatches; }
    public int getContadorAprovados() { return contadorAprovados; }
    public int getContadorCriticos()  { return contadorCriticos; }
    public int getContadorBloqueios() { return contadorBloqueios; }
}
