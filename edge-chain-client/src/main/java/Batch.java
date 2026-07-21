import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * Batch  ---  LOTE DE LEITURAS AGREGADO NO EDGE NODE (DUALCHAIN)
 * ============================================================================
 *
 * Estrutura pedida no enunciado: agrupa varias SensorReading, calcula um hash
 * SHA-256 de todo o lote e expoe um RESUMO (min/max/media/qtd/janela). A LISTA
 * COMPLETA de leituras permanece AQUI (no Edge Node), apenas para auditoria; a
 * blockchain recebe somente o hash + os metadados do lote.
 *
 *   - batchId        : identificador do lote (usado tambem na mainchain);
 *   - hash           : SHA-256 (hex) de todas as leituras (integridade);
 *   - timestamp      : instante de fechamento do lote;
 *   - readings       : leituras originais (NAO vao para a chain);
 *   - totalReadings  : quantidade de leituras;
 *   - min/max/avg    : estatisticas resumidas;
 *   - firstTs/lastTs : janela temporal do lote.
 * ============================================================================
 */
public class Batch {

    private final long   batchId;       // identificador numerico (usado como batchId na mainchain)
    private final String deviceAddress; // dispositivo dono do lote (msg.sender na chain)
    private final List<SensorReading> readings = new ArrayList<>();

    private String     hash;        // SHA-256 (hex) do lote — calculado no fechamento
    private long       timestamp;   // instante (epoch s) do fechamento do lote

    // Estatisticas resumidas (calculadas de forma incremental, O(1) por leitura).
    private BigInteger min;
    private BigInteger max;
    private BigInteger sum = BigInteger.ZERO;
    private BigInteger last;
    private BigInteger firstTs;
    private BigInteger lastTs;
    private long       inicioJanelaMs; // relogio local do inicio da janela (gatilho por tempo)
    private boolean    critico;        // algum evento critico dentro da janela

    public Batch(long batchId, String deviceAddress) {
        this.batchId = batchId;
        this.deviceAddress = deviceAddress;
    }

    /** Adiciona uma leitura ao lote, atualizando as estatisticas resumidas. */
    public void addReading(SensorReading r, boolean critico) {
        if (readings.isEmpty()) {
            min = r.getTemperature();
            max = r.getTemperature();
            sum = BigInteger.ZERO;
            firstTs = r.getTimestamp();
            inicioJanelaMs = System.currentTimeMillis();
            this.critico = false;
        } else {
            if (r.getTemperature().compareTo(min) < 0) min = r.getTemperature();
            if (r.getTemperature().compareTo(max) > 0) max = r.getTemperature();
        }
        readings.add(r);
        last = r.getTemperature();
        sum = sum.add(r.getTemperature());
        lastTs = r.getTimestamp();
        if (critico) this.critico = true;
    }

    public boolean isEmpty()          { return readings.isEmpty(); }
    public int     getTotalReadings() { return readings.size(); }
    public boolean isCritico()        { return critico; }
    public long    getInicioJanelaMs(){ return inicioJanelaMs; }
    public long    getBatchId()       { return batchId; }
    public String  getBatchLabel()    { return "batch-" + batchId; }
    public String  getDeviceAddress() { return deviceAddress; }
    public String  getHash()          { return hash; }
    public long    getTimestamp()     { return timestamp; }
    public List<SensorReading> getReadings() { return readings; }

    public BigInteger getMin()     { return min != null ? min : BigInteger.ZERO; }
    public BigInteger getMax()     { return max != null ? max : BigInteger.ZERO; }
    public BigInteger getLast()    { return last != null ? last : BigInteger.ZERO; }
    public BigInteger getFirstTs() { return firstTs != null ? firstTs : BigInteger.ZERO; }
    public BigInteger getLastTs()  { return lastTs != null ? lastTs : BigInteger.ZERO; }

    public BigInteger getAvg() {
        int n = readings.size();
        return (n > 0) ? sum.divide(BigInteger.valueOf(n)) : BigInteger.ZERO;
    }

    /**
     * Fecha o lote: calcula o SHA-256 de todas as leituras (integridade) e fixa o
     * timestamp de fechamento. Retorna o hash como 32 bytes (para o parametro
     * bytes32 do contrato). A lista de leituras permanece intacta para auditoria.
     */
    public byte[] fechar() {
        StringBuilder sb = new StringBuilder();
        sb.append(batchId).append(';');
        for (SensorReading r : readings) {
            sb.append(r.toCanonical()).append(';');
        }
        byte[] digest = sha256(sb.toString());
        this.hash = toHex(digest);
        this.timestamp = System.currentTimeMillis() / 1000;
        return digest;
    }

    private static byte[] sha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            // SHA-256 sempre existe na JVM; fallback defensivo.
            throw new RuntimeException("SHA-256 indisponivel: " + e.getMessage(), e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
