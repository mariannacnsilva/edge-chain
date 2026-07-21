import java.math.BigInteger;
import java.util.Random;

/**
 * ============================================================================
 * DispositivoIoTDualEdge  ---  SENSOR IoT NA DUALCHAIN COM EDGE NODE (BATCH)
 * ============================================================================
 *
 * Espelha a simulacao de sensor de DispositivoIoT (mesma geracao de temperatura,
 * mesmos limiares e o mesmo perfil MALICIOSO), mas muda o FLUXO DE ENVIO: em vez
 * de chamar a sidechain a cada leitura, entrega a leitura ao EDGE NODE:
 *
 *      Sensor  ->  Edge Node   (chamada local, sem transacao/gas/RPC)
 *
 * O Edge Node agrega, calcula o hash SHA-256 e envia apenas o RESUMO do lote a
 * sidechain (e, se aprovado, o hash a mainchain). O dispositivo nao conhece mais
 * Web3j nem contratos.
 *
 * PERFIL MALICIOSO: dispara uma rajada de leituras com o timestamp CONGELADO. O
 * Edge Node as agrupa em lotes concentrados no mesmo instante, que a sidechain
 * detecta como flood (penalidade/bloqueio) -- preservando a deteccao existente,
 * agora avaliada por lote.
 * ============================================================================
 */
public class DispositivoIoTDualEdge implements Runnable {

    private final int dispositivoId;
    private final EdgeNodeDual edgeNode;
    private final boolean malicioso;
    private volatile boolean ativo;

    // Simulacao de sensor (identica ao DispositivoIoT para comparacao justa).
    private double temperaturaBase;
    private double temperaturaAtual;
    private final double variacaoMaxima;
    private final Random random;

    private static final double LIMITE_CRITICO_ALTO = 45.0;
    private static final double LIMITE_CRITICO_BAIXO = -10.0;
    private static final double LIMITE_AVISO = 40.0;

    // Cadencia realista do sensor (sem a latencia da blockchain a cada leitura).
    private static final long INTERVALO_LEITURA_MS = 100;
    // Rajada maliciosa: leituras concentradas no MESMO instante (flood por lote).
    private static final int TAMANHO_RAJADA = 400;

    private int totalLeituras;
    private int totalAlertas;
    private boolean rajadaEnviada = false;

    public DispositivoIoTDualEdge(int dispositivoId, EdgeNodeDual edgeNode, boolean malicioso) {
        this.dispositivoId = dispositivoId;
        this.edgeNode = edgeNode;
        this.malicioso = malicioso;
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
        System.out.println("[Dispositivo " + dispositivoId + "]: iniciado (DUALCHAIN EDGE)"
                + (malicioso ? " [MALICIOSO]" : ""));

        try {
            while (ativo) {
                lerTemperatura();

                if (malicioso && !rajadaEnviada) {
                    enviarRajadaMaliciosa();
                    rajadaEnviada = true;
                } else {
                    // Entrega a leitura ao Edge Node (sem tocar a blockchain aqui).
                    BigInteger temperatura = BigInteger.valueOf(Math.round(temperaturaAtual));
                    BigInteger timestamp = BigInteger.valueOf(System.currentTimeMillis() / 1000);
                    edgeNode.receberLeitura(dispositivoId, temperatura, timestamp);
                }

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
        double variacao = (random.nextDouble() - 0.5) * 2 * variacaoMaxima;
        temperaturaAtual += variacao;

        long cicloSegundos = (System.currentTimeMillis() / 1000) % 3600;
        double tendencia = Math.sin((cicloSegundos / 3600.0) * 2 * Math.PI) * 2;
        temperaturaAtual += tendencia * 0.01;

        temperaturaAtual = Math.max(-50, Math.min(150, temperaturaAtual));
        totalLeituras++;
    }

    /**
     * Rajada maliciosa: envia muitas leituras com o timestamp CONGELADO ao Edge
     * Node. Os lotes resultantes ficam concentrados no mesmo instante e a sidechain
     * os classifica como flood (penalidade/bloqueio por lote).
     */
    private void enviarRajadaMaliciosa() {
        System.out.println("[Dispositivo " + dispositivoId + "]: (MALICIOSO) enviando rajada de "
                + TAMANHO_RAJADA + " leituras (timestamp congelado) ao Edge Node...");
        BigInteger timestamp = BigInteger.valueOf(System.currentTimeMillis() / 1000); // congelado
        for (int i = 0; i < TAMANHO_RAJADA && ativo; i++) {
            BigInteger temperatura = BigInteger.valueOf(Math.round(temperaturaAtual));
            edgeNode.receberLeitura(dispositivoId, temperatura, timestamp);
            totalLeituras++;
        }
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
        System.out.println("ESTATISTICAS DO DISPOSITIVO " + dispositivoId + " (DUALCHAIN EDGE)");
        System.out.println("=".repeat(60));
        System.out.println("Total de Leituras (enviadas ao Edge Node): " + totalLeituras);
        System.out.println("Total de Alertas (locais): " + totalAlertas);
        System.out.println("Perfil: " + (malicioso ? "MALICIOSO" : "normal"));
        System.out.println("Temperatura Atual: " + String.format("%.2f°C", temperaturaAtual));
        System.out.println("=".repeat(60) + "\n");
    }

    public void stop() { ativo = false; }

    public int getDispositivoId() { return dispositivoId; }
    public int getTotalLeituras() { return totalLeituras; }
    public int getTotalAlertas()  { return totalAlertas; }
}
