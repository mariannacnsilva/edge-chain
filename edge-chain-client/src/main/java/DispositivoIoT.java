import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

public class DispositivoIoT implements Runnable {

    // Configurações do Dispositivo
    private int dispositivoId;
    private Web3j web3j;
    private EdgechainRegulator regulador;   // contrato da SIDECHAIN
    private Credentials credenciais;
    private String deviceType = "temperature";
    private boolean ativo;

    // Configurações de Sensor
    private double temperaturaBase;
    private double temperaturaAtual;
    private double variacaoMaxima; // ±ºC por leitura
    private long intervaloLeituraMs; // ms entre leituras
    private Random random;

    // Limiares de Alerta (o limite critico definitivo (>45) e checado na mainchain)
    private static final double LIMITE_CRITICO_ALTO = 45.0;
    private static final double LIMITE_CRITICO_BAIXO = -10.0;
    private static final double LIMITE_AVISO = 40.0;

    // Estatísticas
    private int totalLeituras;
    private int totalAlertas;
    private int totalAprovadas;
    private int totalRejeitadas;
    private GerenciadorDispositivos gerenciador;

    private boolean malicioso;  // se true, envia uma rajada de leituras
    private boolean rajadaEnviada = false; // controla envio unico da rajada
    private boolean bloqueadoTemporariamente = false; // estado observado de bloqueio temporario
    private static final int TAMANHO_RAJADA = 150;

    public DispositivoIoT(int dispositivoId, Web3j web3j, Credentials credenciais, String contratoAddr, GerenciadorDispositivos gerenciador, boolean malicioso) {
        this.dispositivoId = dispositivoId;
        this.credenciais = credenciais;
        this.web3j = web3j;
        this.malicioso = malicioso;
        this.regulador = EdgechainRegulator.load(contratoAddr, web3j, credenciais,
            BigInteger.valueOf(20_000_000_000L),
            BigInteger.valueOf(500_000));
        this.gerenciador = gerenciador;

        this.random = new Random();
        this.temperaturaBase = 20.0 + (random.nextDouble() * 10);
        this.temperaturaAtual = this.temperaturaBase;
        this.variacaoMaxima = 0.5;
        this.totalLeituras = 0;
        this.totalAlertas = 0;
        this.totalAprovadas = 0;
        this.totalRejeitadas = 0;
    }

    @Override
    public void run() {
        ativo = true;
        System.out.println("[Dispositivo " + dispositivoId + "]: iniciado ");

        try {
            int operationCount = 0;

            while (ativo) {
                execucaoCicloDispositivo();
                operationCount++;
                System.out.println("[Dispositivo " + dispositivoId + "]: " + "Operação #" + operationCount + " concluída");
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

    private void exibirEstatisticas() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ESTATÍSTICAS DO DISPOSITIVO " + dispositivoId);
        System.out.println("=".repeat(60));
        System.out.println("Total de Leituras: " + totalLeituras);
        System.out.println("Total de Alertas: " + totalAlertas);
        System.out.println("Leituras Aprovadas (Sidechain): " + totalAprovadas);
        System.out.println("Leituras Rejeitadas (Sidechain): " + totalRejeitadas);
        System.out.println("Perfil: " + (malicioso ? "MALICIOSO" : "normal") + (bloqueadoTemporariamente ? " (bloqueado)" : ""));
        System.out.println("Temperatura Atual: " + String.format("%.2f°C", temperaturaAtual));
        System.out.println("=".repeat(60) + "\n");
    }

    private void execucaoCicloDispositivo() throws Exception {
        // 1. Simula sensor
        lerTemperatura();

        // 2. Envia a leitura para o REGULADOR (sidechain).
        if (malicioso && !rajadaEnviada) {
            // Dispositivo malicioso: dispara uma rajada para exceder o limite de taxa
            // da sidechain e ser penalizado/bloqueado (demonstra os passos 5 e 12).
            enviarRajadaMaliciosa();
            rajadaEnviada = true;
        } else {
            // Operacao central da dualchain: validacao + reputacao + encaminhamento.
            enviarLeituraRegulador(0); // operationType 0 = leitura periodica
        }

        // 3. Alertas locais (registro/observabilidade no dispositivo)
        verificarAlertas();
    }

    private void lerTemperatura() {
        // Variação natural de temperatura (movimento browniano)
        double variacao = (random.nextDouble() - 0.5) * 2 * variacaoMaxima;
        temperaturaAtual += variacao;

        // Tendência periódica (simulando ciclos naturais)
        long cicloSegundos = (System.currentTimeMillis() / 1000) % 3600;
        double tendencia = Math.sin((cicloSegundos / 3600.0) * 2 * Math.PI) * 2;
        temperaturaAtual += tendencia * 0.01;

        // Limitar flutuações extremas
        temperaturaAtual = Math.max(-50, Math.min(150, temperaturaAtual));

        totalLeituras++;

        System.out.println("[Dispositivo " + dispositivoId + "]: " + "Leitura " + totalLeituras + " - Temperatura: " + temperaturaAtual + "°C");
    }

    private void enviarLeituraRegulador(long operationType) throws Exception {
        try {
            BigInteger temperatura = BigInteger.valueOf((long) Math.round(temperaturaAtual));
            BigInteger timestamp = BigInteger.valueOf(System.currentTimeMillis() / 1000);
            String deviceId = "sensor-" + dispositivoId;

            // --- SIDECHAIN: valida e registra a leitura ---
            TransactionReceipt receipt = regulador.registerTemperatureReading(
                deviceId,
                deviceType,
                BigInteger.valueOf(operationType),
                temperatura,
                timestamp
            ).send();

            gerenciador.registrarTransacao(); // metrica de TPS (tx enviada a sidechain)
            gerenciador.getMetricas().registrarLeituraSidechain(credenciais.getAddress(), receipt.getGasUsed()); // metrica: gas/latencia/TPS sidechain

            // Le o evento ReadingValidated para saber se foi aprovada.
            List<EdgechainRegulator.ReadingValidatedEventResponse> eventos =
                regulador.getReadingValidatedEvents(receipt);

            boolean aprovada = false;
            for (EdgechainRegulator.ReadingValidatedEventResponse ev : eventos) {
                aprovada = ev.approved;
                if (ev.approved) {
                    totalAprovadas++;
                    System.out.println("[Dispositivo " + dispositivoId + "]: Leitura APROVADA pela sidechain (gas sidechain: " + receipt.getGasUsed() + ")");
                } else {
                    totalRejeitadas++;
                    System.out.println("[Dispositivo " + dispositivoId + "]: Leitura REJEITADA pela sidechain");
                }
            }

            // Bloqueio TEMPORARIO: o dispositivo NAO para -- registra o estado e continua.
            // As leituras seguintes sao rejeitadas ate a reabilitacao pela sidechain.
            List<EdgechainRegulator.DeviceBlockedEventResponse> bloqueios =
                regulador.getDeviceBlockedEvents(receipt);
            if (!bloqueios.isEmpty()) {
                bloqueadoTemporariamente = true;
                System.out.println("[Dispositivo " + dispositivoId + "]: BLOQUEADO TEMPORARIAMENTE pela sidechain. Leituras serao rejeitadas ate a reabilitacao.");
                return; // nao encaminha a mainchain nesta leitura
            }

            // Reabilitacao: se estava bloqueado e voltou a ser aprovado (ou emitiu
            // DeviceUnblocked), limpa o estado de bloqueio.
            if (bloqueadoTemporariamente
                    && (aprovada || !regulador.getDeviceUnblockedEvents(receipt).isEmpty())) {
                bloqueadoTemporariamente = false;
                System.out.println("[Dispositivo " + dispositivoId + "]: REABILITADO pela sidechain. Voltando a operar normalmente.");
            }

            // --- MAINCHAIN: encaminha a operacao aprovada (via relayer) ---
            if (aprovada) {
                // A mainchain sinaliza temperatura critica. NAO desligamos mais o
                // dispositivo: apenas REGISTRAMOS o alerta e seguimos operando.
                boolean alertaCritico = gerenciador.encaminharParaMainchain(
                    credenciais.getAddress(), operationType, temperatura, timestamp);
                if (alertaCritico) {
                    totalAlertas++;
                    System.out.println("[Dispositivo " + dispositivoId + "]: MAINCHAIN sinalizou ALERTA CRITICO (temperatura critica). Alerta registrado; dispositivo continua operando.");
                }
            }
        } catch (Exception e) {
            System.out.println("[Dispositivo " + dispositivoId + "]: Erro ao enviar leitura ao regulador: " + e.getMessage());
        }
    }

    private void enviarRajadaMaliciosa() {
        System.out.println("[Dispositivo " + dispositivoId + "]: (MALICIOSO) enviando rajada de " + TAMANHO_RAJADA + " leituras para exceder o limite da sidechain...");
        String deviceId = "sensor-" + dispositivoId;
        BigInteger timestamp = BigInteger.valueOf(System.currentTimeMillis() / 1000);
        BigInteger temperatura = BigInteger.valueOf((long) Math.round(temperaturaAtual));

        for (int i = 0; i < TAMANHO_RAJADA && ativo; i++) {
            try {
                TransactionReceipt receipt = regulador.registerTemperatureReading(
                    deviceId, deviceType, BigInteger.ZERO, temperatura, timestamp
                ).send();

                gerenciador.registrarTransacao();
                gerenciador.getMetricas().registrarLeituraSidechain(credenciais.getAddress(), receipt.getGasUsed());

                if (!regulador.getDeviceBlockedEvents(receipt).isEmpty()) {
                    bloqueadoTemporariamente = true;
                    totalRejeitadas++;
                    System.out.println("[Dispositivo " + dispositivoId + "]: (MALICIOSO) BLOQUEADO TEMPORARIAMENTE pela sidechain apos " + (i + 1) + " leituras.");
                    break;
                }
            } catch (Exception e) {
                System.out.println("[Dispositivo " + dispositivoId + "]: (MALICIOSO) erro na rajada: " + e.getMessage());
                break;
            }
        }
    }

    private void verificarAlertas() {
        String tipoAlerta = null;

        if (temperaturaAtual > LIMITE_CRITICO_ALTO) {
            tipoAlerta = "CRÍTICO ALTO";
        } else if (temperaturaAtual < LIMITE_CRITICO_BAIXO) {
            tipoAlerta = "CRÍTICO BAIXO";
        } else if (temperaturaAtual > LIMITE_AVISO) {
            tipoAlerta = "AVISO";
        }

        if (tipoAlerta != null) {
            totalAlertas++;
            System.out.println("[Dispositivo " + dispositivoId + "]: ALERTA " + tipoAlerta + " detectado! Temp: " + String.format("%.2f°C", temperaturaAtual));
        }
    }

    public void stop() {
        ativo = false;
    }

    // Getters
    public int getDispositivoId() {
        return dispositivoId;
    }

    public double getTemperaturaAtual() {
        return temperaturaAtual;
    }

    public int getTotalLeituras() {
        return totalLeituras;
    }

    public int getTotalAlertas() {
        return totalAlertas;
    }
}
