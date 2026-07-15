// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

/**
 * ============================================================================
 * EdgechainMain  ---  CONTRATO PRINCIPAL (MAINCHAIN) -- VERSAO OTIMIZADA
 * ============================================================================
 *
 * RESPONSABILIDADE (inalterada):
 * Executa APENAS operacoes ja APROVADAS pela sidechain. Sua unica logica e:
 *   1. Registrar a operacao de temperatura aprovada (metricas).
 *   2. Verificar se a temperatura ultrapassou o limite critico.
 *   3. Sinalizar se o dispositivo deve ser desligado (deviceShouldShutdown).
 *   4. Devolver o custo (gas) para a sidechain.
 *
 * ----------------------------------------------------------------------------
 * CORRECOES / OTIMIZACOES DESTA VERSAO:
 *
 *  (1) LIMITE CRITICO ALINHADO AO CLIENTE. Antes o limite era 80, mas o sensor
 *      IoT (DispositivoIoT) usa LIMITE_CRITICO_ALTO = 45. Como as temperaturas
 *      simuladas raramente passavam de 80, a mainchain "nunca" gerava alertas.
 *      Agora CRITICAL_TEMPERATURE = 45: qualquer temperatura acima do limite
 *      REAL do cliente gera alerta critico e ordem de desligamento.
 *
 *  (2) SEM ESCRITA DE lastExecution NO CAMINHO QUENTE. A struct lastExecution
 *      era regravada (6 SSTORE) a cada operacao e nao era usada pelo experimento
 *      (o relayer le o EVENTO CriticalAlert, nao lastExecution). Removida do
 *      caminho quente; getLastExecution permanece na ABI.
 *
 *  (3) MENOS EVENTOS. TemperatureProcessed nao era consumido (o relayer usa o
 *      gas real do receipt). Sai do caminho quente; so CriticalAlert e emitido,
 *      pois e o que dispara o desligamento na sidechain.
 *
 *  (4) SEM SSTORE REDUNDANTE em criticalAlert (so escreve quando o valor muda).
 * ============================================================================
 */
contract EdgechainMain {

    // Limiar de temperatura critica alinhado ao cliente IoT (LIMITE_CRITICO_ALTO = 45).
    uint256 public constant CRITICAL_TEMPERATURE = 45;

    address public owner;
    address public regulator;

    // Estado global de alerta critico.
    bool public criticalAlert;

    // Metricas para comparacao singlechain x dualchain.
    uint256 public totalOperations;
    uint256 public totalCriticalAlerts;

    // Mantida na ABI (getLastExecution / lastExecution) por compatibilidade,
    // mas NAO e regravada no caminho quente (economia de gas).
    struct LastExecution {
        address device;
        uint256 temperature;
        uint256 timestamp;
        bool temperatureCritical;
        bool deviceShouldShutdown;
        uint256 gasUsed;
    }
    LastExecution public lastExecution;

    // Mantido na ABI, mas nao emitido no caminho quente.
    event TemperatureProcessed(
        address indexed device,
        uint256 operationType,
        uint256 temperature,
        uint256 timestamp,
        uint256 gasUsed
    );

    // Essencial: dispara o desligamento do dispositivo na sidechain.
    event CriticalAlert(
        address indexed device,
        uint256 temperature,
        uint256 timestamp,
        bool deviceShouldShutdown
    );

    modifier onlyRegulator() {
        require(
            regulator == address(0) || msg.sender == regulator || msg.sender == owner,
            "Apenas o regulador (sidechain) pode executar operacoes"
        );
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    function setRegulator(address _regulator) public {
        require(msg.sender == owner, "Apenas owner pode configurar o regulador");
        regulator = _regulator;
    }

    /**
     * Recebe a operacao aprovada e executa a logica principal.
     * Retorna (temperatureCritical, deviceShouldShutdown, gasUsed).
     */
    function executeTemperatureOperation(
        address device,
        uint256 operationType,
        uint256 temperature,
        uint256 timestamp
    )
        public
        onlyRegulator
        returns (bool temperatureCritical, bool deviceShouldShutdown, uint256 gasUsed)
    {
        uint256 startGas = gasleft();
        totalOperations++;

        // --- Verificacao de temperatura critica ---
        if (temperature > CRITICAL_TEMPERATURE) {
            if (!criticalAlert) {
                criticalAlert = true; // evita SSTORE redundante
            }
            temperatureCritical = true;
            deviceShouldShutdown = true;
            totalCriticalAlerts++;
            emit CriticalAlert(device, temperature, timestamp, true);
        } else if (criticalAlert) {
            criticalAlert = false; // evita SSTORE redundante
        }
        // (silencia o warning de parametro nao usado no caminho normal)
        operationType;

        // --- Custo da execucao (usado apenas no modo MESMA EVM) ---
        gasUsed = (startGas - gasleft()) + 21000;

        return (temperatureCritical, deviceShouldShutdown, gasUsed);
    }

    // --- Getters auxiliares (ABI preservada) ---

    function getTotalOperations() public view returns (uint256) {
        return totalOperations;
    }

    function getLastExecution()
        public
        view
        returns (
            address device,
            uint256 temperature,
            uint256 timestamp,
            bool temperatureCritical,
            bool deviceShouldShutdown,
            uint256 gasUsed
        )
    {
        LastExecution memory l = lastExecution;
        return (l.device, l.temperature, l.timestamp, l.temperatureCritical, l.deviceShouldShutdown, l.gasUsed);
    }
}
