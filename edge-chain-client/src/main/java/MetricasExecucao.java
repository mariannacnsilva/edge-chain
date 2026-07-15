import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coletor de metricas de execucao da arquitetura DUALCHAIN.
 *
 * Esta classe e ADITIVA: nao altera a logica dos contratos nem das funcoes ja
 * implementadas. Ela apenas acumula dados a partir dos TransactionReceipt que o
 * cliente ja obtem (gas) e do relogio local (latencia/TPS), reportando ao final:
 *
 *   1. quantidade de leituras realizadas na mainchain
 *   2. quantidade de leituras realizadas na sidechain
 *   3. quantidade de gas utilizado por dispositivo
 *   4. quantidade de gas total utilizado na mainchain
 *   5. quantidade de gas total utilizado na sidechain
 *   6. latencia mainchain
 *   7. latencia sidechain
 *   8. tps mainchain
 *   9. tps sidechain
 *
 * Observacao: como o web3j 4.1.1 gera as funcoes "view" como transacao (nao
 * decodifica o retorno), as metricas sao coletadas no lado Java a partir do gas
 * dos receipts, e nao lidas do estado on-chain.
 */
public class MetricasExecucao {

    // ---- Sidechain (EdgechainRegulator.registerTemperatureReading) ----
    private final AtomicLong sideLeituras = new AtomicLong(0);
    private final AtomicLong sideGasTotal = new AtomicLong(0);
    private volatile long sidePrimeiraMs = 0;
    private volatile long sideUltimaMs = 0;

    // ---- Mainchain (EdgechainMain.executeTemperatureOperation) ----
    private final AtomicLong mainLeituras = new AtomicLong(0);
    private final AtomicLong mainGasTotal = new AtomicLong(0);
    private volatile long mainPrimeiraMs = 0;
    private volatile long mainUltimaMs = 0;

    // ---- Gas por dispositivo (endereco -> gas) ----
    private final Map<String, Long> gasSidePorDispositivo = new ConcurrentHashMap<>();
    private final Map<String, Long> gasMainPorDispositivo = new ConcurrentHashMap<>();

    // ---- Singlechain (EdgeChain.any_operation) -- fluxo unico, para comparacao ----
    private final AtomicLong singleLeituras = new AtomicLong(0);
    private final AtomicLong singleGasTotal = new AtomicLong(0);
    private volatile long singlePrimeiraMs = 0;
    private volatile long singleUltimaMs = 0;
    private final Map<String, Long> gasSinglePorDispositivo = new ConcurrentHashMap<>();

    /** Registra uma leitura processada na SIDECHAIN (chamada apos o receipt). */
    public synchronized void registrarLeituraSidechain(String device, BigInteger gasUsado) {
        long agora = System.currentTimeMillis();
        if (sidePrimeiraMs == 0) sidePrimeiraMs = agora;
        sideUltimaMs = agora;

        long g = (gasUsado != null) ? gasUsado.longValue() : 0L;
        sideLeituras.incrementAndGet();
        sideGasTotal.addAndGet(g);
        gasSidePorDispositivo.merge(device, g, Long::sum);
    }

    /** Registra uma operacao processada na MAINCHAIN (chamada apos o receipt). */
    public synchronized void registrarLeituraMainchain(String device, BigInteger gasUsado) {
        long agora = System.currentTimeMillis();
        if (mainPrimeiraMs == 0) mainPrimeiraMs = agora;
        mainUltimaMs = agora;

        long g = (gasUsado != null) ? gasUsado.longValue() : 0L;
        mainLeituras.incrementAndGet();
        mainGasTotal.addAndGet(g);
        gasMainPorDispositivo.merge(device, g, Long::sum);
    }

    /**
     * Registra uma operacao processada na SINGLECHAIN (EdgeChain.any_operation).
     * Fluxo unico usado pelo runner de comparacao SingleIoTBlockchain.
     */
    public synchronized void registrarOperacaoSinglechain(String device, BigInteger gasUsado) {
        long agora = System.currentTimeMillis();
        if (singlePrimeiraMs == 0) singlePrimeiraMs = agora;
        singleUltimaMs = agora;

        long g = (gasUsado != null) ? gasUsado.longValue() : 0L;
        singleLeituras.incrementAndGet();
        singleGasTotal.addAndGet(g);
        gasSinglePorDispositivo.merge(device, g, Long::sum);
    }

    // ---- Getters das 9 metricas ----

    public long getLeiturasMainchain() { return mainLeituras.get(); }        // (1)
    public long getLeiturasSidechain() { return sideLeituras.get(); }        // (2)
    public long getGasTotalMainchain() { return mainGasTotal.get(); }        // (4)
    public long getGasTotalSidechain() { return sideGasTotal.get(); }        // (5)

    /** Gas total (sidechain + mainchain) por dispositivo. (3) */
    public Map<String, Long> getGasPorDispositivo() {
        Map<String, Long> total = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : gasSidePorDispositivo.entrySet()) {
            total.merge(e.getKey(), e.getValue(), Long::sum);
        }
        for (Map.Entry<String, Long> e : gasMainPorDispositivo.entrySet()) {
            total.merge(e.getKey(), e.getValue(), Long::sum);
        }
        return total;
    }

    /** Duracao (ms) da janela de leituras da mainchain. */
    public long getDuracaoMainchainMs() {
        return (mainUltimaMs >= mainPrimeiraMs && mainPrimeiraMs > 0) ? (mainUltimaMs - mainPrimeiraMs) : 0;
    }

    /** Duracao (ms) da janela de leituras da sidechain. */
    public long getDuracaoSidechainMs() {
        return (sideUltimaMs >= sidePrimeiraMs && sidePrimeiraMs > 0) ? (sideUltimaMs - sidePrimeiraMs) : 0;
    }

    /** Latencia media por operacao na mainchain (ms/op). (6) */
    public double getLatenciaMainchainMs() {
        long n = mainLeituras.get();
        return (n > 0) ? (double) getDuracaoMainchainMs() / n : 0.0;
    }

    /** Latencia media por operacao na sidechain (ms/op). (7) */
    public double getLatenciaSidechainMs() {
        long n = sideLeituras.get();
        return (n > 0) ? (double) getDuracaoSidechainMs() / n : 0.0;
    }

    /** TPS da mainchain. (8) */
    public double getTpsMainchain() {
        long dur = getDuracaoMainchainMs();
        return (dur > 0) ? mainLeituras.get() / (dur / 1000.0) : 0.0;
    }

    /** TPS da sidechain. (9) */
    public double getTpsSidechain() {
        long dur = getDuracaoSidechainMs();
        return (dur > 0) ? sideLeituras.get() / (dur / 1000.0) : 0.0;
    }

    // ---- Getters do fluxo SINGLECHAIN (comparacao) ----

    public long getLeiturasSinglechain() { return singleLeituras.get(); }
    public long getGasTotalSinglechain() { return singleGasTotal.get(); }

    public Map<String, Long> getGasSinglePorDispositivo() {
        return new LinkedHashMap<>(gasSinglePorDispositivo);
    }

    public long getDuracaoSinglechainMs() {
        return (singleUltimaMs >= singlePrimeiraMs && singlePrimeiraMs > 0) ? (singleUltimaMs - singlePrimeiraMs) : 0;
    }

    /** Latencia media por operacao na singlechain (ms/op). */
    public double getLatenciaSinglechainMs() {
        long n = singleLeituras.get();
        return (n > 0) ? (double) getDuracaoSinglechainMs() / n : 0.0;
    }

    /** TPS da singlechain. */
    public double getTpsSinglechain() {
        long dur = getDuracaoSinglechainMs();
        return (dur > 0) ? singleLeituras.get() / (dur / 1000.0) : 0.0;
    }

    /** Imprime o relatorio completo das 9 metricas. */
    public void imprimirRelatorio() {
        System.out.println("\n" + "=".repeat(64));
        System.out.println("RELATORIO DE METRICAS DA EXECUCAO (DUALCHAIN)");
        System.out.println("=".repeat(64));
        System.out.println("(1) Leituras na MAINCHAIN        : " + getLeiturasMainchain());
        System.out.println("(2) Leituras na SIDECHAIN        : " + getLeiturasSidechain());
        System.out.println("(4) Gas total MAINCHAIN          : " + getGasTotalMainchain());
        System.out.println("(5) Gas total SIDECHAIN          : " + getGasTotalSidechain());
        System.out.printf ("(6) Latencia MAINCHAIN           : %.2f ms/op (janela %d ms)%n",
                getLatenciaMainchainMs(), getDuracaoMainchainMs());
        System.out.printf ("(7) Latencia SIDECHAIN           : %.2f ms/op (janela %d ms)%n",
                getLatenciaSidechainMs(), getDuracaoSidechainMs());
        System.out.printf ("(8) TPS MAINCHAIN                : %.2f tx/s%n", getTpsMainchain());
        System.out.printf ("(9) TPS SIDECHAIN                : %.2f tx/s%n", getTpsSidechain());
        System.out.println("-".repeat(64));
        System.out.println("(3) Gas por dispositivo (sidechain + mainchain):");
        Map<String, Long> gasTotal = getGasPorDispositivo();
        if (gasTotal.isEmpty()) {
            System.out.println("    (nenhum dispositivo registrado)");
        } else {
            for (Map.Entry<String, Long> e : gasTotal.entrySet()) {
                long side = gasSidePorDispositivo.getOrDefault(e.getKey(), 0L);
                long main = gasMainPorDispositivo.getOrDefault(e.getKey(), 0L);
                System.out.printf("    %s -> total: %d (side: %d | main: %d)%n",
                        e.getKey(), e.getValue(), side, main);
            }
        }
        System.out.println("=".repeat(64) + "\n");
    }

    /**
     * Imprime o relatorio da execucao SINGLECHAIN (fluxo unico), no mesmo formato
     * do relatorio dualchain para permitir a comparacao direta entre arquiteturas.
     *
     * Mapeamento de comparacao com a dualchain:
     *   - leituras (singlechain)  <-> leituras SIDECHAIN (dualchain) = nº de leituras logicas
     *   - gas total (singlechain) <-> gas total (SIDECHAIN + MAINCHAIN) (dualchain)
     *   - latencia / TPS          <-> latencia / TPS da dualchain (por chain e agregado)
     */
    public void imprimirRelatorioSinglechain() {
        System.out.println("\n" + "=".repeat(64));
        System.out.println("RELATORIO DE METRICAS DA EXECUCAO (SINGLECHAIN - EdgeChain)");
        System.out.println("=".repeat(64));
        System.out.println("(1) Leituras (SINGLECHAIN)       : " + getLeiturasSinglechain());
        System.out.println("(2) Gas total (SINGLECHAIN)          : " + getGasTotalSinglechain());
        System.out.printf ("(3) Latencia (SINGLECHAIN)           : %.2f ms/op (janela %d ms)%n",getLatenciaSinglechainMs(), getDuracaoSinglechainMs());
        System.out.printf ("(4) TPS (SINGLECHAIN)                : %.2f tx/s%n", getTpsSinglechain());
        System.out.println("-".repeat(64));
        System.out.println("Gas por dispositivo (SINGLECHAIN):");
        Map<String, Long> gasSingle = getGasSinglePorDispositivo();
        if (gasSingle.isEmpty()) {
            System.out.println("    (nenhum dispositivo registrado)");
        } else {
            for (Map.Entry<String, Long> e : gasSingle.entrySet()) {
                System.out.printf("    %s -> total: %d%n", e.getKey(), e.getValue());
            }
        }
    }
}
