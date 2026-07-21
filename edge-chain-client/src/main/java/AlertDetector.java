import java.math.BigInteger;

/**
 * ============================================================================
 * AlertDetector  ---  DETECCAO DE TEMPERATURA CRITICA (EDGE COMPUTING)
 * ============================================================================
 *
 * Responsavel por, no Edge Node, classificar uma leitura como critica antes de
 * ela chegar a blockchain. Quando uma leitura e critica, o Edge Node deve
 * disparar o envio IMEDIATO do batch (nao espera encher o lote nem o tempo).
 *
 * Os limiares sao os MESMOS ja usados pelo dispositivo IoT (DispositivoIoTSingle:
 * LIMITE_CRITICO_ALTO = 45, LIMITE_CRITICO_BAIXO = -10) e pelo contrato
 * (EdgechainMain.CRITICAL_TEMPERATURE = 45), garantindo alinhamento com a
 * implementacao existente.
 * ============================================================================
 */
public class AlertDetector {

    private final int limiteCriticoAlto;
    private final int limiteCriticoBaixo;

    /** Limiares default alinhados ao cliente IoT e ao contrato. */
    public AlertDetector() {
        this(45, -10);
    }

    public AlertDetector(int limiteCriticoAlto, int limiteCriticoBaixo) {
        this.limiteCriticoAlto  = limiteCriticoAlto;
        this.limiteCriticoBaixo = limiteCriticoBaixo;
    }

    /** true se a temperatura ultrapassa o limite superior ou inferior. */
    public boolean isCritico(BigInteger temperatura) {
        int t = temperatura.intValue();
        return t > limiteCriticoAlto || t < limiteCriticoBaixo;
    }

    /** Rotulo do alerta (para log/observabilidade), ou null se nao for critico. */
    public String tipoAlerta(BigInteger temperatura) {
        int t = temperatura.intValue();
        if (t > limiteCriticoAlto)  return "CRITICO ALTO";
        if (t < limiteCriticoBaixo) return "CRITICO BAIXO";
        return null;
    }

    public int getLimiteCriticoAlto()  { return limiteCriticoAlto; }
    public int getLimiteCriticoBaixo() { return limiteCriticoBaixo; }
}
