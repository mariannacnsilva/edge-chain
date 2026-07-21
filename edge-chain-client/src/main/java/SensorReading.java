import java.math.BigInteger;

/**
 * ============================================================================
 * SensorReading  ---  LEITURA INDIVIDUAL DE UM SENSOR (fica no Edge Node)
 * ============================================================================
 *
 * Representa UMA leitura crua de um dispositivo IoT. Estas leituras sao
 * armazenadas COMPLETAS apenas em memoria no Edge Node (para auditoria); para a
 * blockchain vai somente o RESUMO do lote (Batch) + o hash SHA-256.
 *
 * O conteudo canonico (deviceId|temperature|timestamp) tambem alimenta o calculo
 * do hash do lote, garantindo integridade/rastreabilidade dos dados originais.
 * ============================================================================
 */
public class SensorReading {

    private final String     deviceId;      // ex.: "sensor-3"
    private final String     deviceAddress; // endereco on-chain do dispositivo (msg.sender)
    private final BigInteger temperature;   // temperatura (inteiro, como o contrato espera)
    private final BigInteger timestamp;     // epoch em segundos

    public SensorReading(String deviceId, String deviceAddress, BigInteger temperature, BigInteger timestamp) {
        this.deviceId = deviceId;
        this.deviceAddress = deviceAddress;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public String     getDeviceId()      { return deviceId; }
    public String     getDeviceAddress() { return deviceAddress; }
    public BigInteger getTemperature()   { return temperature; }
    public BigInteger getTimestamp()     { return timestamp; }

    /** Forma canonica usada no calculo do hash do lote. */
    public String toCanonical() {
        return deviceId + "|" + temperature + "|" + timestamp;
    }
}
