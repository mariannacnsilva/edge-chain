import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.util.Random;

public class DispositivoIoT implements Runnable{
    
    // Configurações do Dispositivo
    private int dispositivoId;
    private Web3j web3j;
    private HelloWorld contrato;
    private Credentials credenciais;
    private boolean ativo;

    // Configurações de Sensor
    private double temperaturaBase;
    private double temperaturaAtual;
    private double variacaoMaxima; // ±ºC por leitura
    private long intervaloLeituraMs; // ms entre leituras
    private Random random;

    // Limiares de Alerta
    private static final double LIMITE_CRITICO_ALTO = 80.0;
    private static final double LIMITE_CRITICO_BAIXO = -10.0;
    private static final double LIMITE_AVISO = 75.0;
    
    // Estatísticas
    private int totalLeituras;
    private int totalAlertas;
    private int totalSincronizacoes;
    private long ultimaSincronizacao;

    public DispositivoIoT(int dispositivoId, Web3j web3j, Credentials credenciais, String contratoAddr, long intervaloLeituraMs) {
        this.dispositivoId = dispositivoId;
        this.credenciais = credenciais;
        this.web3j = web3j;
        this.intervaloLeituraMs = intervaloLeituraMs;
        this.contrato = HelloWorld.load(contratoAddr, web3j, credenciais, 
            BigInteger.valueOf(20_000_000_000L), 
            BigInteger.valueOf(300_000));

        // Inicializar sensor com valores realistas
        this.random = new Random();
        this.temperaturaBase = 20.0 + (random.nextDouble() * 10); // 20-30ºC
        this.temperaturaAtual = this.temperaturaBase;
        this.variacaoMaxima = 0.5; // ±0.5ºC por leitura
        
        // Estatísticas
        this.totalLeituras = 0;
        this.totalAlertas = 0;
        this.totalSincronizacoes = 0;
        this.ultimaSincronizacao = System.currentTimeMillis();
    }

    @Override
    public void run() {
        ativo = true;
        System.out.println("[Dispositivo " + dispositivoId + "]: iniciado ");
        
        try {
    
            int operationCount = 0;

            while (ativo) {
                execucaoCicloDispositivo();

                Thread.sleep(intervaloLeituraMs + ((long)(random.nextDouble() * intervaloLeituraMs * 0.2 - intervaloLeituraMs * 0.1))); // ±10% de variação no intervalo
                
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
        System.out.println("Total de Sincronizações: " + totalSincronizacoes);
        System.out.println("Temperatura Atual: " + String.format("%.2f°C", temperaturaAtual));
        System.out.println("=".repeat(60) + "\n");
    }

    private void execucaoCicloDispositivo() throws Exception {

        // 1. Simula sensor
        lerTemperatura();
        
        // 2. OPERAÇÕES PRÁTICAS (Ciclo normal de IoT)
        // 2.1. PING/Heartbeat - Verificar conectividade (grátis)
        if (totalLeituras % 2 == 0) realizarPing();
        
        // 2.2. SINCRONIZAÇÃO - Verificar versão do firmware (grátis)
        if (totalLeituras % 5 == 0) sincronizarComBlokcchain();
        
        // 2.3. ATUALIZAÇÃO - Atualizar versão se necessário (transação)
        if (totalLeituras % 15 == 0) atualizarFirmware();
        
        // 2.4. HISTÓRICO - Consultar contador de operações (grátis)
        if (totalLeituras % 10 == 0) consultarHistorico();

        // 3. VERIFICAÇÃO DE ALERTAS
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
        
        System.out.println("[Dispositivo " + dispositivoId + "]: " + "Leitura "+ totalLeituras +" - Temperatura: "+ temperaturaAtual +"°C");
    }

    private void realizarPing() throws Exception {
        try {
            byte[] response = contrato.hi().send();
            String status = new String(response).trim();
            System.out.println("[Dispositivo " + dispositivoId + "]: Ping enviado - Status: " + status);
        } catch (Exception e) {
            System.out.println("[Dispositivo " + dispositivoId + "]: Falha no ping: " + e.getMessage());
        }
    }

    private void sincronizarComBlokcchain() throws Exception {
        try {
            // Obter versão atual (verificação leve no contrato)
            BigInteger version = contrato.getVersion().send();
            System.out.println("[Dispositivo " + dispositivoId + "]: Heartbeat enviado. Versão do contrato: " + version);
            
            totalSincronizacoes++;
            ultimaSincronizacao = System.currentTimeMillis();
            
        } catch (Exception e) {
            System.out.println("[Dispositivo " + dispositivoId + "]: Erro na sincronização: " + e.getMessage());
        }
    }

    private void atualizarFirmware() throws Exception {
        try {
            BigInteger versaoAtual = contrato.getVersion().send();
            BigInteger novaVersao = versaoAtual.add(BigInteger.ONE);
            
            System.out.println("[Dispositivo " + dispositivoId + "]: Atualizando firmware de " + versaoAtual + " para " + novaVersao);
            
            TransactionReceipt receipt = contrato.setVersion(novaVersao).send();
            
            if (receipt.getStatus().equals("0x1")) {
                System.out.println("[Dispositivo " + dispositivoId + "]: Firmware atualizado! Hash: " + receipt.getTransactionHash().substring(0, 10) + "... Gas: " + receipt.getGasUsed());
            } else {
                System.out.println("[Dispositivo " + dispositivoId + "]: Falha na atualização de firmware");
            }
            
        } catch (Exception e) {
            if (e.getMessage().contains("superior")) {
                System.out.println("[Dispositivo " + dispositivoId + "]: Versão já é a mais recente");
            } else {
                System.out.println("[Dispositivo " + dispositivoId + "]: Erro na atualização: " + e.getMessage());
            }
        }
    }

    private void consultarHistorico() throws Exception {
        try {
            BigInteger totalOps = contrato.getHellonum().send();
            System.out.println("[Dispositivo " + dispositivoId + "]: Histórico - Total de eventos registrados: " + totalOps);
        } catch (Exception e) {
            System.out.println("[Dispositivo " + dispositivoId + "]: Erro ao consultar histórico: " + e.getMessage());
        }
    }

    private void verificarAlertas() throws Exception {
        String tipoAlerta = null;
        
        if (temperaturaAtual > LIMITE_CRITICO_ALTO) {
            tipoAlerta = "CRÍTICO ALTO";
        } else if (temperaturaAtual < LIMITE_CRITICO_BAIXO) {
            tipoAlerta = "CRÍTICO BAIXO";
        } else if (temperaturaAtual > LIMITE_AVISO) {
            tipoAlerta = "AVISO";
        }
        
        if (tipoAlerta != null) {
            enviarAlerta(tipoAlerta);
        }
    }

    private void enviarAlerta(String tipoAlerta) throws Exception {
        try {
            System.out.println("[Dispositivo " + dispositivoId + "]: ALERTA " + tipoAlerta + " detectado! Temp: " + String.format("%.2f°C", temperaturaAtual));
            
            // Registra alerta no blockchain (hinofree - transação com custo)
            TransactionReceipt receipt = contrato.hinofree().send();
            
            System.out.println("[Dispositivo " + dispositivoId + "]: Alerta enviado ao blockchain - Gas usado: "+ receipt.getGasUsed() +", Status: " + receipt.getStatus());
            
            totalAlertas++;
            
        } catch (Exception e) {
            System.out.println("[Dispositivo " + dispositivoId + "]: Falha ao enviar alerta: " + e.getMessage());
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
