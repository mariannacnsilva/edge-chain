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

    // ---- Edge Computing (EdgeNode + EdgeChain.any_operation em BATCH) ----
    // Fluxo com camada de borda: N leituras recebidas -> 1 batch enviado ao contrato.
    private final AtomicLong edgeLeiturasRecebidas = new AtomicLong(0); // leituras que chegaram ao Edge Node
    private final AtomicLong edgeLeiturasAgregadas = new AtomicLong(0); // leituras efetivamente consolidadas em batches enviados
    private final AtomicLong edgeBatches           = new AtomicLong(0); // batches (transacoes) enviados ao contrato
    private final AtomicLong edgeGasTotal          = new AtomicLong(0); // gas total gasto nos batches
    private final AtomicLong edgeLatenciaBatchMsTotal = new AtomicLong(0); // soma das latencias de envio dos batches
    private volatile long edgePrimeiraMs = 0;
    private volatile long edgeUltimaMs = 0;
    private final Map<String, Long> gasEdgePorDispositivo = new ConcurrentHashMap<>();

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

    // ========================================================================
    // EDGE COMPUTING (camada de borda sobre a Singlechain)
    // ========================================================================

    /** Registra uma leitura recebida pelo Edge Node (nao gera transacao). */
    public void registrarLeituraRecebidaEdge() {
        edgeLeiturasRecebidas.incrementAndGet();
    }

    /**
     * Registra o envio de UM batch ao contrato (1 transacao any_operation que
     * consolida numLeituras leituras). Chamado apos o receipt.
     *
     * @param device      endereco do dispositivo (msg.sender do batch)
     * @param gasUsado    gas do receipt do batch
     * @param numLeituras leituras consolidadas neste batch
     * @param latenciaMs  tempo de envio do batch (ms)
     */
    public synchronized void registrarBatchEdge(String device, BigInteger gasUsado, long numLeituras, long latenciaMs) {
        long agora = System.currentTimeMillis();
        if (edgePrimeiraMs == 0) edgePrimeiraMs = agora;
        edgeUltimaMs = agora;

        long g = (gasUsado != null) ? gasUsado.longValue() : 0L;
        edgeBatches.incrementAndGet();
        edgeLeiturasAgregadas.addAndGet(numLeituras);
        edgeGasTotal.addAndGet(g);
        edgeLatenciaBatchMsTotal.addAndGet(Math.max(0L, latenciaMs));
        gasEdgePorDispositivo.merge(device, g, Long::sum);
    }

    // ---- Getters do fluxo EDGE ----

    public long getEdgeLeiturasRecebidas() { return edgeLeiturasRecebidas.get(); }
    public long getEdgeLeiturasAgregadas() { return edgeLeiturasAgregadas.get(); }
    public long getEdgeBatches()           { return edgeBatches.get(); }
    public long getEdgeGasTotal()          { return edgeGasTotal.get(); }

    public Map<String, Long> getGasEdgePorDispositivo() {
        return new LinkedHashMap<>(gasEdgePorDispositivo);
    }

    public long getDuracaoEdgeMs() {
        return (edgeUltimaMs >= edgePrimeiraMs && edgePrimeiraMs > 0) ? (edgeUltimaMs - edgePrimeiraMs) : 0;
    }

    /** Fator medio de agregacao = leituras consolidadas / batches enviados. */
    public double getFatorAgregacaoEdge() {
        long b = edgeBatches.get();
        return (b > 0) ? (double) edgeLeiturasAgregadas.get() / b : 0.0;
    }

    /**
     * Economia percentual de TRANSACOES vs. Singlechain: na Singlechain cada
     * leitura recebida seria 1 transacao; no Edge, sao apenas edgeBatches.
     */
    public double getEconomiaTransacoesPct() {
        long recebidas = edgeLeiturasRecebidas.get();
        if (recebidas <= 0) return 0.0;
        return (1.0 - (double) edgeBatches.get() / recebidas) * 100.0;
    }

    /**
     * Economia percentual de GAS (estimativa). Baseline: cada leitura recebida
     * custaria ~1 any_operation na Singlechain, ao gas medio medido por batch
     * (mesma funcao do contrato). Assim baseline = recebidas * gasMedioPorBatch e
     * a economia = 1 - gasEdge/baseline. Como o gas por op e o mesmo, a economia
     * de gas acompanha de perto a economia de transacoes.
     */
    public double getEconomiaGasEstimadaPct() {
        long batches = edgeBatches.get();
        long recebidas = edgeLeiturasRecebidas.get();
        long gasEdge = edgeGasTotal.get();
        if (batches <= 0 || recebidas <= 0 || gasEdge <= 0) return 0.0;
        double gasMedioPorBatch = (double) gasEdge / batches;
        double baseline = recebidas * gasMedioPorBatch; // Singlechain estimada (1 op/leitura)
        return (1.0 - gasEdge / baseline) * 100.0;
    }

    /** TPS efetivo do Edge (batches/segundo na janela de envios). */
    public double getTpsEfetivoEdge() {
        long dur = getDuracaoEdgeMs();
        return (dur > 0) ? edgeBatches.get() / (dur / 1000.0) : 0.0;
    }

    /** Latencia media por BATCH (ms de envio por batch). */
    public double getLatenciaMediaBatchMs() {
        long b = edgeBatches.get();
        return (b > 0) ? (double) edgeLatenciaBatchMsTotal.get() / b : 0.0;
    }

    /**
     * Latencia media por LEITURA (ms de blockchain amortizados por leitura):
     * custo total de envio dividido pelas leituras consolidadas. Mostra como a
     * agregacao dilui a latencia da blockchain entre muitas leituras.
     */
    public double getLatenciaMediaPorLeituraMs() {
        long ag = edgeLeiturasAgregadas.get();
        return (ag > 0) ? (double) edgeLatenciaBatchMsTotal.get() / ag : 0.0;
    }

    /** Latencia media por operacao on-chain (batch), no MESMO formato da singlechain. */
    public double getLatenciaEdgeMs() {
        long n = edgeBatches.get();
        return (n > 0) ? (double) getDuracaoEdgeMs() / n : 0.0;
    }

    /**
     * Relatorio da execucao EDGE no MESMO padrao/formato do relatorio SINGLECHAIN
     * (imprimirRelatorioSinglechain), apresentando os mesmos valores: leituras,
     * gas total, latencia, TPS e gas por dispositivo. Aqui a "operacao on-chain" e
     * o BATCH (any_operation), unidade equivalente a leitura da singlechain (onde
     * cada leitura era 1 any_operation). Assim os dois relatorios ficam diretamente
     * comparaveis linha a linha.
     *
     * Em seguida, um bloco complementar exibe as metricas especificas da camada de
     * borda (agregacao/economia), exigidas pelo experimento.
     */
    public void imprimirRelatorioEdge() {
        System.out.println("\n" + "=".repeat(64));
        System.out.println("RELATORIO DE METRICAS DA EXECUCAO (EDGE - EdgeChain)");
        System.out.println("=".repeat(64));
        System.out.println("(1) Leituras (EDGE)       : " + getEdgeBatches());
        System.out.println("(2) Gas total (EDGE)          : " + getEdgeGasTotal());
        System.out.printf ("(3) Latencia (EDGE)           : %.2f ms/op (janela %d ms)%n", getLatenciaEdgeMs(), getDuracaoEdgeMs());
        System.out.printf ("(4) TPS (EDGE)                : %.2f tx/s%n", getTpsEfetivoEdge());
        System.out.println("-".repeat(64));
        System.out.println("Gas por dispositivo (EDGE):");
        Map<String, Long> gasEdge = getGasEdgePorDispositivo();
        if (gasEdge.isEmpty()) {
            System.out.println("    (nenhum dispositivo registrado)");
        } else {
            for (Map.Entry<String, Long> e : gasEdge.entrySet()) {
                System.out.printf("    %s -> total: %d%n", e.getKey(), e.getValue());
            }
        }

        // --- Bloco complementar: metricas da camada de borda (agregacao) ---
        System.out.println("-".repeat(64));
        System.out.println("METRICAS ADICIONAIS DA CAMADA EDGE (agregacao):");
        System.out.println("    Leituras recebidas pelo Edge Node : " + getEdgeLeiturasRecebidas());
        System.out.println("    Leituras agregadas (em batches)   : " + getEdgeLeiturasAgregadas());
        System.out.println("    Batches enviados ao contrato      : " + getEdgeBatches());
        System.out.printf ("    Fator medio de agregacao          : %.1f leituras/batch%n", getFatorAgregacaoEdge());
        System.out.printf ("    Economia de transacoes            : %.1f%%%n", getEconomiaTransacoesPct());
        System.out.printf ("    Economia de gas (estimada)        : %.1f%%%n", getEconomiaGasEstimadaPct());
        System.out.printf ("    Latencia media por batch          : %.2f ms/batch%n", getLatenciaMediaBatchMs());
        System.out.printf ("    Latencia media por leitura        : %.2f ms/leitura%n", getLatenciaMediaPorLeituraMs());
    }
}
