import java.math.BigInteger;

/**
 * ============================================================================
 * DeviceBuffer  ---  BUFFER DE AGREGACAO POR DISPOSITIVO (EDGE COMPUTING)
 * ============================================================================
 *
 * Mantido em MEMORIA no Edge Node, um buffer por dispositivo. Em vez de guardar
 * todas as leituras individualmente, guarda apenas o RESUMO consolidado exigido
 * pela especificacao:
 *
 *   - quantidade de leituras;
 *   - temperatura minima;
 *   - temperatura maxima;
 *   - temperatura media (via soma acumulada / contador);
 *   - ultima leitura;
 *   - timestamp inicial;
 *   - timestamp final;
 *   - indicador de evento critico.
 *
 * Esta estrutura e ADITIVA e reaproveita o mesmo estilo do "Batch" ja usado na
 * arquitetura dualchain (GerenciadorDispositivos.Batch): nenhuma leitura e
 * armazenada uma-a-uma, apenas os agregados. Todas as temperaturas sao tratadas
 * como inteiros (BigInteger), exatamente como o contrato EdgeChain espera.
 * ============================================================================
 */
public class DeviceBuffer {

    private long       count;        // quantidade de leituras acumuladas desde o ultimo flush
    private BigInteger min;          // temperatura minima do batch
    private BigInteger max;          // temperatura maxima do batch
    private BigInteger last;         // ultima temperatura recebida
    private BigInteger sum;          // soma das temperaturas (media = sum / count)
    private BigInteger primeiroTs;   // timestamp inicial do batch
    private BigInteger ultimoTs;     // timestamp final do batch
    private boolean    critico;      // indicador de evento critico dentro da janela
    private long       inicioJanelaMs; // relogio local do inicio da janela (gatilho por tempo)

    /**
     * Adiciona uma leitura ao buffer, atualizando apenas os agregados.
     *
     * @param temperatura leitura (inteiro, como o contrato espera)
     * @param timestamp   timestamp da leitura (segundos/epoch, BigInteger)
     * @param critico     true se a leitura ja foi classificada como critica pelo AlertDetector
     */
    public void adicionar(BigInteger temperatura, BigInteger timestamp, boolean critico) {
        if (count == 0) {
            // Primeira leitura de uma nova janela: reinicia os agregados.
            min = temperatura;
            max = temperatura;
            sum = BigInteger.ZERO;
            primeiroTs = timestamp;
            this.critico = false;
            inicioJanelaMs = System.currentTimeMillis();
        } else {
            if (temperatura.compareTo(min) < 0) min = temperatura;
            if (temperatura.compareTo(max) > 0) max = temperatura;
        }
        count++;
        last = temperatura;
        sum = sum.add(temperatura);
        ultimoTs = timestamp;
        if (critico) this.critico = true;
    }

    /** true se ha leituras acumuladas ainda nao enviadas. */
    public boolean temLeituras() {
        return count > 0;
    }

    public long getCount()          { return count; }
    public boolean isCritico()      { return critico; }
    public long getInicioJanelaMs() { return inicioJanelaMs; }

    /** Temperatura media do batch (0 se vazio). */
    public BigInteger getMedia() {
        if (count == 0) return BigInteger.ZERO;
        return sum.divide(BigInteger.valueOf(count));
    }

    /**
     * Tira uma "fotografia" imutavel do resumo atual e ZERA o buffer, iniciando
     * uma nova janela de agregacao. Retornar o resumo e resetar de forma atomica
     * (sob o lock do EdgeNode) permite enviar a transacao para a blockchain FORA
     * da regiao critica, sem bloquear novas leituras do dispositivo.
     */
    public Resumo consolidarEReiniciar() {
        Resumo r = new Resumo(count, min, max, getMedia(), last, primeiroTs, ultimoTs, critico);
        count = 0; // proxima leitura recomeca a janela (adicionar() reinicia o resto)
        critico = false;
        return r;
    }

    /**
     * Resumo consolidado e imutavel de um batch — exatamente o conjunto de campos
     * que o Edge Node encaminha para a blockchain.
     */
    public static final class Resumo {
        public final long       leituras;
        public final BigInteger min;
        public final BigInteger max;
        public final BigInteger media;
        public final BigInteger ultima;
        public final BigInteger tsInicial;
        public final BigInteger tsFinal;
        public final boolean    critico;

        Resumo(long leituras, BigInteger min, BigInteger max, BigInteger media,
               BigInteger ultima, BigInteger tsInicial, BigInteger tsFinal, boolean critico) {
            this.leituras  = leituras;
            this.min       = min;
            this.max       = max;
            this.media     = media;
            this.ultima    = ultima;
            this.tsInicial = tsInicial;
            this.tsFinal   = tsFinal;
            this.critico   = critico;
        }
    }
}
