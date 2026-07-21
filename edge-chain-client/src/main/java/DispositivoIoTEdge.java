import java.math.BigInteger;
import java.util.Random;

/**
 * ============================================================================
 * DispositivoIoTEdge  ---  SENSOR IoT NA ARQUITETURA COM EDGE COMPUTING
 * ============================================================================
 *
 * Espelha a simulacao de sensor de DispositivoIoTSingle (mesma geracao de
 * temperatura e mesmos limiares de alerta, para uma comparacao justa), mas com
 * UMA MUDANCA de fluxo: em vez de chamar diretamente o contrato a cada leitura,
 * o dispositivo entrega a leitura ao EDGE NODE:
 *
 *      Sensor  ->  Edge Node   (chamada local, sem transacao/gas/RPC)
 *
 * O Edge Node e quem decide, por agregacao/filtragem, quando acionar o contrato.
 * Assim o dispositivo nao conhece mais Web3j nem o contrato — a camada de borda
 * cuida disso. As leituras sao geradas em uma cadencia realista de sensor
 * (INTERVALO_LEITURA_MS), ja que nao ha mais a latencia da blockchain a cada
 * leitura para "segurar" o laco.
 * ============================================================================
 */
public class DispositivoIoTEdge implements Runnable {

    private final int dispositivoId;
    private final EdgeNode edgeNode;
    private volatile boolean ativo;

    // Simulacao de sensor (identica ao DispositivoIoTSingle para comparacao justa).
    private double temperaturaBase;
    private double temperaturaAtual;
    private final double variacaoMaxima; // ±ºC por leitura
    private final Random random;

    // Limiares de alerta (iguais aos do DispositivoIoTSingle).
    private static final double LIMITE_CRITICO_ALTO = 45.0;
    private static final double LIMITE_CRITICO_BAIXO = -10.0;
    private static final double LIMITE_AVISO = 40.0;

    // Cadencia de amostragem do sensor. Sem a latencia da blockchain a cada
    // leitura, o laco precisa de um intervalo realista (senao geraria leituras
    // em loop-quente). 100 ms => ~10 leituras/s por dispositivo.
    private static final long INTERVALO_LEITURA_MS = 100;

    // Estatisticas locais (observabilidade no dispositivo).
    private int totalLeituras;
    private int totalAlertas;

    public DispositivoIoTEdge(int dispositivoId, EdgeNode edgeNode) {
        this.dispositivoId = dispositivoId;
        this.edgeNode = edgeNode;
        this.random = new Random();
        this.temperaturaBase = 20.0 + (random.nextDouble() * 10);
        this.temperaturaAtual = this.temperaturaBase;
        this.variacaoMaxima = 0.5;
        this.totalLeituras = 0;
        this.totalAlertas = 0;
    }

    @Override
    public void run() {
        ativo = true;
        System.out.println("[Dispositivo " + dispositivoId + "]: iniciado (EDGE)");

        try {
            while (ativo) {
                // 1. Simula o sensor (gera a leitura de temperatura).
                lerTemperatura();

                // 2. Entrega a leitura ao Edge Node (SEM tocar a blockchain aqui).
                BigInteger temperatura = BigInteger.valueOf(Math.round(temperaturaAtual));
                BigInteger timestamp = BigInteger.valueOf(System.currentTimeMillis() / 1000);
                edgeNode.receberLeitura(dispositivoId, temperatura, timestamp);

                // 3. Alertas locais (observabilidade no dispositivo).
                verificarAlertas();

                Thread.sleep(INTERVALO_LEITURA_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[Dispositivo " + dispositivoId + "]: Dispositivo interrompido");
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        } finally {
            exibirEstatisticas();
        }
    }

    private void lerTemperatura() {
        // Variacao natural de temperatura (movimento browniano).
        double variacao = (random.nextDouble() - 0.5) * 2 * variacaoMaxima;
        temperaturaAtual += variacao;

        // Tendencia periodica (simulando ciclos naturais).
        long cicloSegundos = (System.currentTimeMillis() / 1000) % 3600;
        double tendencia = Math.sin((cicloSegundos / 3600.0) * 2 * Math.PI) * 2;
        temperaturaAtual += tendencia * 0.01;

        // Limitar flutuacoes extremas.
        temperaturaAtual = Math.max(-50, Math.min(150, temperaturaAtual));

        totalLeituras++;
    }

    private void verificarAlertas() {
        String tipoAlerta = null;

        if (temperaturaAtual > LIMITE_CRITICO_ALTO) {
            tipoAlerta = "CRITICO ALTO";
        } else if (temperaturaAtual < LIMITE_CRITICO_BAIXO) {
            tipoAlerta = "CRITICO BAIXO";
        } else if (temperaturaAtual > LIMITE_AVISO) {
            tipoAlerta = "AVISO";
        }

        if (tipoAlerta != null) {
            totalAlertas++;
            System.out.println("[Dispositivo " + dispositivoId + "]: ALERTA " + tipoAlerta
                    + " detectado! Temp: " + String.format("%.2f°C", temperaturaAtual));
        }
    }

    private void exibirEstatisticas() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ESTATISTICAS DO DISPOSITIVO " + dispositivoId + " (EDGE)");
        System.out.println("=".repeat(60));
        System.out.println("Total de Leituras (enviadas ao Edge Node): " + totalLeituras);
        System.out.println("Total de Alertas (locais): " + totalAlertas);
        System.out.println("Temperatura Atual: " + String.format("%.2f°C", temperaturaAtual));
        System.out.println("=".repeat(60) + "\n");
    }

    public void stop() {
        ativo = false;
    }

    public int getDispositivoId() { return dispositivoId; }
    public double getTemperaturaAtual() { return temperaturaAtual; }
    public int getTotalLeituras() { return totalLeituras; }
    public int getTotalAlertas() { return totalAlertas; }
}
