/**
 * ============================================================================
 * BatchManager  ---  POLITICA DE ENVIO DO BATCH (EDGE COMPUTING)
 * ============================================================================
 *
 * Decide QUANDO um DeviceBuffer deve ser consolidado e encaminhado a blockchain.
 * O Edge Node so aciona o contrato quando ocorre UMA das condicoes previstas na
 * especificacao:
 *
 *   (a) quantidade minima de leituras atingida ....... loteMin      (ex.: 50)
 *   (b) tempo maximo de janela atingido .............. janelaMs     (ex.: 60s)
 *   (c) evento critico detectado ..................... flush imediato
 *
 * Mantendo esta decisao isolada, o EdgeNode fica simples e a politica de
 * agregacao passa a ser facilmente configuravel/testavel a partir do runner.
 * Os gatilhos espelham os ja usados na dualchain (loteMin + tempo + critico).
 * ============================================================================
 */
public class BatchManager {

    private final int  loteMin;    // (a) quantidade minima de leituras para consolidar
    private final long janelaMs;   // (b) tempo maximo (ms) que uma janela pode ficar aberta

    /** Defaults da especificacao: 50 leituras OU 60 segundos. */
    public BatchManager() {
        this(50, 60_000L);
    }

    public BatchManager(int loteMin, long janelaMs) {
        this.loteMin  = loteMin;
        this.janelaMs = janelaMs;
    }

    /**
     * @return true se o buffer deve ser enviado agora (lote cheio, tempo excedido
     *         ou evento critico). Se o buffer estiver vazio, retorna false.
     */
    public boolean deveEnviar(DeviceBuffer buffer, long agoraMs) {
        return deveEnviar((int) buffer.getCount(), buffer.isCritico(), buffer.getInicioJanelaMs(), agoraMs);
    }

    /** Motivo do envio (para log/observabilidade). */
    public String motivoEnvio(DeviceBuffer buffer, long agoraMs) {
        return motivoEnvio((int) buffer.getCount(), buffer.isCritico(), buffer.getInicioJanelaMs(), agoraMs);
    }

    /**
     * Forma primitiva da politica de envio, reutilizada tanto pelo buffer da
     * SingleChain (DeviceBuffer) quanto pelo lote da DualChain (Batch).
     */
    public boolean deveEnviar(int count, boolean critico, long inicioJanelaMs, long agoraMs) {
        if (count <= 0) return false;
        boolean loteCheio     = count >= loteMin;                          // (a)
        boolean tempoExcedido = (agoraMs - inicioJanelaMs) >= janelaMs;    // (b)
        return loteCheio || tempoExcedido || critico;                      // (c)
    }

    public String motivoEnvio(int count, boolean critico, long inicioJanelaMs, long agoraMs) {
        if (critico)                                    return "CRITICO";
        if (count >= loteMin)                           return "LOTE_CHEIO";
        if ((agoraMs - inicioJanelaMs) >= janelaMs)     return "TEMPO";
        return "-";
    }

    public int  getLoteMin()  { return loteMin; }
    public long getJanelaMs() { return janelaMs; }
}
