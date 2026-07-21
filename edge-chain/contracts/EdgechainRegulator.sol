// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

/**
 * Interface minima da mainchain, usada quando o regulador e a mainchain
 * estao na MESMA EVM (chamada direta contrato->contrato). Em modo cross-chain
 * (duas redes Ganache/Geth distintas) a chamada e feita off-chain pelo
 * relayer Java, e esta interface nao e usada -- ver bloco try/catch abaixo.
 */
interface IEdgechainMain {
    function executeTemperatureOperation(
        address device,
        uint256 operationType,
        uint256 temperature,
        uint256 timestamp
    ) external returns (bool temperatureCritical, bool deviceShouldShutdown, uint256 gasUsed);
}

/**
 * ============================================================================
 * EdgechainRegulator  ---  CONTRATO REGULADOR (SIDECHAIN) -- VERSAO OTIMIZADA
 * ============================================================================
 *
 * RESPONSABILIDADE (inalterada em relacao a versao original):
 *   - cadastro automatico de dispositivos;
 *   - registro de comportamento;
 *   - deteccao de comportamento anormal (flood de requisicoes);
 *   - penalidade / bloqueio / reputacao;
 *   - aprovacao ou rejeicao da solicitacao;
 *   - reencaminhamento das solicitacoes VALIDAS para a mainchain;
 *   - atualizacao de custo e recompensa apos a execucao na mainchain.
 *
 * ----------------------------------------------------------------------------
 * OTIMIZACOES DESTA VERSAO (mesma interface publica; so muda o "como"):
 *
 *  (A) HISTORICO -> ESTATISTICAS AGREGADAS.
 *      A versao anterior guardava um ARRAY ReadingHistory[] por dispositivo e,
 *      a cada leitura acima de 50, fazia um SHIFT O(n) do array (dezenas de
 *      SSTORE por leitura). Isso dominava o gas e derrubava o TPS. Agora
 *      guardamos apenas AGREGADOS de custo O(1): ultima leitura, ultimo
 *      timestamp, contagem, soma de temperatura (para a media) e o primeiro
 *      timestamp (para a frequencia media). Nenhum loop, nenhum array.
 *
 *  (B) RECOMPENSA SEM transfer() NO CAMINHO QUENTE.
 *      A versao anterior fazia payable(device).transfer(reward) a CADA leitura
 *      (chamada externa + risco de revert se o contrato ficasse sem saldo, o
 *      que parava a execucao cedo). Agora a recompensa e apenas CALCULADA e
 *      PERSISTIDA (dev.reward); o pagamento pode ser liquidado fora do caminho
 *      quente. Remove chamada externa, remove o risco de revert e corta gas.
 *
 *  (C) MENOS EVENTOS. Emitimos apenas o essencial que o cliente Java LE:
 *      ReadingValidated / DeviceRegistered / DeviceBlocked / DeviceUnblocked.
 *      Os eventos RewardUpdated/RewardPaid (que ninguem consumia) sairam do
 *      caminho quente.
 *
 *  (D) MENOS SSTORE. Escrevemos so os campos que MUDAM (nao regravamos zeros no
 *      cadastro nem reescrevemos dev.reward quando o valor nao muda).
 *
 *  (E) DETECCAO POR INTERVALO (nao por contagem bruta). Ver _evaluateBehavior.
 * ============================================================================
 */
contract EdgechainRegulator {

    // ----------------------------------------------------------------------
    // Constantes de politica
    // ----------------------------------------------------------------------
    uint256 private constant MAX_PENALTY_LEVEL = 5;    // penaltyLevel > 5 => bloqueia
    uint256 private constant BURST_LIMIT       = 50;   // leituras no MESMO instante (flood) antes de anormal
    uint256 private constant BLOCK_DURATION    = 60;   // duracao do bloqueio temporario (segundos)
    uint256 private constant REPUTATION_START  = 100;  // reputacao inicial
    uint256 private constant REWARD_BASE       = 1000; // base de recompensa

    // ----------------------------------------------------------------------
    // Estrutura de dispositivo. Substituimos o historico completo por
    // ESTATISTICAS AGREGADAS (custo de armazenamento O(1) por dispositivo).
    // ----------------------------------------------------------------------
    struct Device {
        string  deviceId;
        address deviceAddress;
        string  deviceType;
        uint    transactionCount;   // total de leituras (= "quantidade de leituras")
        uint    unsafeOperations;   // leituras suspeitas
        uint    penaltyLevel;       // nivel de penalidade
        uint    reputation;         // reputacao acumulada
        uint    accumulatedGas;     // custo acumulado (gas)
        bool    blocked;            // dispositivo bloqueado?
        bool    exists;             // controle interno de cadastro
        uint    reward;             // ultima recompensa calculada
        uint    lastTimestamp;      // timestamp da ultima leitura (janela de taxa)
        uint    burstCount;         // leituras consecutivas no MESMO timestamp (flood)
        uint    blockedUntil;       // ate quando o bloqueio temporario vale
        // --- estatisticas agregadas (substituem ReadingHistory[]) ---
        uint    lastTemperature;    // ultima temperatura registrada
        uint    tempSum;            // soma das temperaturas (media = tempSum / transactionCount)
        uint    firstTimestamp;     // primeira leitura (frequencia = count / (last - first))
        // --- rastreabilidade de LOTES (Edge Node com hash SHA-256) ---
        bytes32 lastBatchHash;      // hash do ultimo lote validado (auditoria)
        uint    batchCount;         // quantidade de lotes recebidos deste dispositivo
    }

    mapping(address => Device) private devices;
    address[] private deviceList;

    address public owner;
    address public mainchainContract;

    // Metricas globais para comparacao singlechain x dualchain
    uint256 public totalReadings;
    uint256 public totalApproved;
    uint256 public totalRejected;

    // ----------------------------------------------------------------------
    // Eventos (mesmas assinaturas da versao anterior; RewardPaid removido pois
    // nao era consumido por ninguem). Emitimos apenas o essencial.
    // ----------------------------------------------------------------------
    event DeviceRegistered(address indexed device, string deviceId, string deviceType);
    event ReadingValidated(
        address indexed device,
        uint256 temperature,
        uint256 timestamp,
        bool approved,
        bool temperatureCritical,
        bool deviceShouldShutdown
    );
    event DeviceBlocked(address indexed device, uint256 penaltyLevel);
    event DeviceUnblocked(address indexed device);
    // Mantido na ABI por compatibilidade, mas NAO emitido no caminho quente.
    event RewardUpdated(address indexed device, uint256 reward, uint256 reputation, uint256 penaltyLevel);

    // LOTE (Edge Node): resumo do batch validado + hash SHA-256 (rastreabilidade).
    event BatchValidated(
        address indexed device,
        bytes32 batchHash,
        uint256 readingCount,
        uint256 avgTemperature,
        bool approved
    );

    constructor() {
        owner = msg.sender;
    }

    // Mantido por compatibilidade com financiarContrato(). Como a recompensa nao
    // e mais paga via transfer no caminho quente, o financiamento passa a ser
    // opcional (sem risco de revert por saldo insuficiente).
    receive() external payable {}

    function setMainchainContract(address _mainchain) public {
        require(msg.sender == owner, "Apenas owner pode configurar a mainchain");
        mainchainContract = _mainchain;
    }

    /**
     * ------------------------------------------------------------------------
     * Ponto de entrada da SIDECHAIN: recebe a leitura do sensor.
     * Retorna: (approved, temperatureCritical, deviceShouldShutdown, gasUsed, reward)
     * ------------------------------------------------------------------------
     */
    function registerTemperatureReading(
        string memory deviceId,
        string memory deviceType,
        uint256 operationType,
        uint256 temperature,
        uint256 timestamp
    )
        public
        returns (
            bool approved,
            bool temperatureCritical,
            bool deviceShouldShutdown,
            uint256 gasUsed,
            uint256 reward
        )
    {
        uint256 startGas = gasleft();
        totalReadings++;

        Device storage dev = devices[msg.sender];

        // --- Cadastro automatico: grava SO os campos nao-zero (economia de SSTORE) ---
        if (!dev.exists) {
            dev.deviceId = deviceId;
            dev.deviceAddress = msg.sender;
            dev.deviceType = deviceType;
            dev.reputation = REPUTATION_START;
            dev.exists = true;
            dev.firstTimestamp = timestamp;
            deviceList.push(msg.sender);
            emit DeviceRegistered(msg.sender, deviceId, deviceType);
        }

        // --- Bloqueio TEMPORARIO ---
        if (dev.blocked) {
            if (timestamp >= dev.blockedUntil) {
                dev.blocked = false;
                dev.penaltyLevel = MAX_PENALTY_LEVEL; // abaixo do limite de bloqueio (>5)
                dev.lastTimestamp = timestamp;        // reinicia a janela de avaliacao
                dev.burstCount = 0;
                emit DeviceUnblocked(msg.sender);
            } else {
                totalRejected++;
                emit ReadingValidated(msg.sender, temperature, timestamp, false, false, false);
                return (false, false, false, 0, dev.reward);
            }
        }

        // --- Registro de comportamento + estatisticas agregadas (O(1)) ---
        dev.transactionCount++;
        dev.lastTemperature = temperature;
        dev.tempSum += temperature;

        // --- Avaliacao de comportamento por INTERVALO entre leituras ---
        bool abnormal = _evaluateBehavior(dev, timestamp);

        if (abnormal) {
            // Flood: penaliza e REJEITA (nao encaminha a mainchain).
            dev.penaltyLevel += 1;
            dev.unsafeOperations += 1;
            dev.reputation = dev.reputation >= 10 ? dev.reputation - 10 : 0;

            if (dev.penaltyLevel > MAX_PENALTY_LEVEL) {
                dev.blocked = true;
                dev.blockedUntil = timestamp + BLOCK_DURATION;
                emit DeviceBlocked(msg.sender, dev.penaltyLevel);
            }

            totalRejected++;
            reward = _updateReward(dev);
            emit ReadingValidated(msg.sender, temperature, timestamp, false, false, false);
            return (false, false, false, 0, reward);
        }

        // --- Comportamento normal: reabilita reputacao gradualmente ---
        if (dev.penaltyLevel > 0) {
            dev.penaltyLevel -= 1;
        }
        dev.reputation += 1;

        // --- Encaminhamento para a mainchain (so no modo MESMA EVM) ---
        // Em cross-chain mainchainContract == address(0) e este bloco e pulado:
        // o encaminhamento definitivo e feito off-chain pelo relayer Java.
        uint256 mainGas = 0;
        if (mainchainContract != address(0)) {
            try IEdgechainMain(mainchainContract).executeTemperatureOperation(
                msg.sender,
                operationType,
                temperature,
                timestamp
            ) returns (bool crit, bool shutdown, uint256 g) {
                temperatureCritical = crit;
                deviceShouldShutdown = shutdown;
                mainGas = g;
            } catch {
                // Mainchain nao acessivel nesta EVM: relayer Java encaminha off-chain.
            }
        }

        totalApproved++;

        // --- Custo da transacao + recompensa (sem transfer) ---
        uint256 sideGas = (startGas - gasleft()) + 21000;
        dev.accumulatedGas += sideGas + mainGas;
        reward = _updateReward(dev);
        gasUsed = sideGas + mainGas;

        emit ReadingValidated(
            msg.sender,
            temperature,
            timestamp,
            true,
            temperatureCritical,
            deviceShouldShutdown
        );

        return (true, temperatureCritical, deviceShouldShutdown, gasUsed, reward);
    }

    /**
     * ------------------------------------------------------------------------
     * PONTO DE ENTRADA EM LOTE (Edge Node) -- NOVO FLUXO BATCH.
     *
     * Recebe apenas o RESUMO de um lote (hash SHA-256 + metadados) em vez de cada
     * leitura individual. Executa EXATAMENTE a mesma logica de cadastro,
     * comportamento, penalidade, reputacao, bloqueio e recompensa, porem UMA vez
     * por lote (representando readingCount leituras). A lista completa de leituras
     * permanece FORA da blockchain (no Edge Node); aqui so guardamos o hash.
     *
     * Retorna: (approved, gasUsed, reward). A criticidade e SEMPRE avaliada na
     * mainchain (modo cross-chain), como no fluxo por leitura -- por isso nao e
     * retornada aqui (a logica quebrada em helpers evita "stack too deep").
     * ------------------------------------------------------------------------
     */
    function registerBatch(
        string memory deviceId,
        string memory deviceType,
        bytes32 batchHash,
        uint256 readingCount,
        uint256 maxTemperature,
        uint256 avgTemperature,
        uint256 firstTimestamp,
        uint256 lastTimestamp
    )
        public
        returns (bool approved, uint256 gasUsed, uint256 reward)
    {
        uint256 startGas = gasleft();
        totalReadings += readingCount; // o lote representa readingCount leituras

        Device storage dev = devices[msg.sender];
        _ensureRegistered(dev, deviceId, deviceType, firstTimestamp);

        // Rastreabilidade do lote: apenas o HASH entra no estado da chain.
        dev.lastBatchHash = batchHash;
        dev.batchCount += 1;

        // Bloqueio + comportamento + reputacao/penalidade (helper => evita stack too deep).
        approved = _validateBatch(dev, readingCount, maxTemperature, avgTemperature, firstTimestamp, lastTimestamp);

        // --- Custo da transacao + recompensa (sem transfer) ---
        gasUsed = (startGas - gasleft()) + 21000;
        if (approved) {
            dev.accumulatedGas += gasUsed;
        }
        reward = _updateReward(dev);

        // Um unico evento por LOTE (em vez de um por leitura). ReadingValidated e
        // mantido para compatibilidade com os leitores Java existentes.
        emit BatchValidated(msg.sender, batchHash, readingCount, avgTemperature, approved);
        emit ReadingValidated(msg.sender, maxTemperature, lastTimestamp, approved, false, false);

        return (approved, gasUsed, reward);
    }

    /** Cadastro automatico de dispositivo (extraido para reduzir a pilha). */
    function _ensureRegistered(
        Device storage dev,
        string memory deviceId,
        string memory deviceType,
        uint256 firstTimestamp
    ) private {
        if (!dev.exists) {
            dev.deviceId = deviceId;
            dev.deviceAddress = msg.sender;
            dev.deviceType = deviceType;
            dev.reputation = REPUTATION_START;
            dev.exists = true;
            dev.firstTimestamp = firstTimestamp;
            deviceList.push(msg.sender);
            emit DeviceRegistered(msg.sender, deviceId, deviceType);
        }
    }

    /**
     * Nucleo da validacao de um LOTE: bloqueio temporario, estatisticas agregadas,
     * deteccao de flood, penalidade/reputacao. Mesma logica do fluxo por leitura,
     * porem aplicada de uma vez ao lote. Retorna se o lote foi aprovado.
     */
    function _validateBatch(
        Device storage dev,
        uint256 readingCount,
        uint256 maxTemperature,
        uint256 avgTemperature,
        uint256 firstTimestamp,
        uint256 lastTimestamp
    ) private returns (bool) {
        // --- Bloqueio TEMPORARIO ---
        if (dev.blocked) {
            if (lastTimestamp >= dev.blockedUntil) {
                dev.blocked = false;
                dev.penaltyLevel = MAX_PENALTY_LEVEL; // abaixo do limite de bloqueio (>5)
                dev.lastTimestamp = lastTimestamp;    // reinicia a janela de avaliacao
                dev.burstCount = 0;
                emit DeviceUnblocked(msg.sender);
            } else {
                totalRejected += readingCount;
                return false; // continua bloqueado: rejeita o lote
            }
        }

        // --- Registro de comportamento + estatisticas agregadas (O(1)) ---
        dev.transactionCount += readingCount;
        dev.lastTemperature = maxTemperature;         // pior caso p/ criticidade na mainchain
        dev.tempSum += avgTemperature * readingCount; // soma ~ media * quantidade

        // --- Avaliacao de comportamento sobre o LOTE ---
        if (_evaluateBatchBehavior(dev, readingCount, firstTimestamp, lastTimestamp)) {
            // Flood: penaliza e REJEITA (nao encaminha a mainchain).
            dev.penaltyLevel += 1;
            dev.unsafeOperations += 1;
            dev.reputation = dev.reputation >= 10 ? dev.reputation - 10 : 0;
            if (dev.penaltyLevel > MAX_PENALTY_LEVEL) {
                dev.blocked = true;
                dev.blockedUntil = lastTimestamp + BLOCK_DURATION;
                emit DeviceBlocked(msg.sender, dev.penaltyLevel);
            }
            totalRejected += readingCount;
            return false;
        }

        // --- Comportamento normal: reabilita reputacao gradualmente ---
        if (dev.penaltyLevel > 0) {
            dev.penaltyLevel -= 1;
        }
        dev.reputation += 1;
        totalApproved += readingCount;
        return true;
    }

    /**
     * Modo CROSS-CHAIN: o relayer Java executa a mainchain, obtem o gas real e
     * reporta de volta aqui para atualizar accumulatedGas e a recompensa.
     * (Sem transfer -- apenas atualiza estado, como o resto do caminho quente.)
     */
    function updateExecutionCost(address device, bool success, uint256 mainGasUsed)
        public
        returns (uint256 reward)
    {
        Device storage dev = devices[device];
        require(dev.exists, "Dispositivo nao cadastrado");
        if (success) {
            dev.accumulatedGas += mainGasUsed;
        }
        reward = _updateReward(dev);
        return reward;
    }

    // ----------------------------------------------------------------------
    // Avaliacao de comportamento por INTERVALO (nao por contagem bruta).
    //
    // Um sensor legitimo carimba cada leitura com o RELOGIO REAL, entao o seu
    // timestamp AVANCA com o tempo. Sempre que o timestamp avanca, a "rajada"
    // (leituras acumuladas no mesmo instante) e zerada -- portanto uma cadencia
    // honesta NUNCA e penalizada, por mais leituras que envie ao longo do tempo.
    //
    // Um atacante que dispara um flood congela o timestamp (envia dezenas/centenas
    // de leituras com o MESMO instante). Nesse caso burstCount cresce sem parar e,
    // ao ultrapassar BURST_LIMIT, a leitura e marcada como anormal.
    //
    // Isso substitui o modelo antigo (contar TODAS as leituras numa janela fixa
    // de 60s), que bloqueava dispositivos normais so por enviarem muitas leituras.
    // ----------------------------------------------------------------------
    function _evaluateBehavior(Device storage dev, uint256 timestamp) private returns (bool) {
        if (dev.lastTimestamp == 0 || timestamp > dev.lastTimestamp) {
            // O tempo avancou (ou primeira leitura): cadencia normal, zera a rajada.
            dev.lastTimestamp = timestamp;
            dev.burstCount = 1;
            return false;
        }
        // Timestamp NAO avancou: leituras empilhadas no mesmo instante (flood).
        dev.burstCount += 1;
        return dev.burstCount > BURST_LIMIT;
    }

    // ----------------------------------------------------------------------
    // Avaliacao de comportamento para LOTES (mesma filosofia do por-leitura).
    //
    // Um lote honesto cobre uma JANELA DE TEMPO (lastTimestamp > firstTimestamp):
    // quando o tempo avanca, a rajada e zerada e a cadencia e considerada normal.
    // Um atacante que dispara um flood concentra muitas leituras no MESMO instante:
    // o lote chega com readingCount alto e firstTimestamp == lastTimestamp, fazendo
    // burstCount ultrapassar BURST_LIMIT (comportamento anormal).
    // ----------------------------------------------------------------------
    function _evaluateBatchBehavior(
        Device storage dev,
        uint256 readingCount,
        uint256 firstTimestamp,
        uint256 lastTimestamp
    ) private returns (bool) {
        if (dev.lastTimestamp == 0 || lastTimestamp > dev.lastTimestamp) {
            dev.lastTimestamp = lastTimestamp;
            // Lote inteiro no mesmo instante => conta todas as leituras como rajada;
            // lote que cobre uma janela de tempo => cadencia normal.
            dev.burstCount = (lastTimestamp > firstTimestamp) ? 1 : readingCount;
            return dev.burstCount > BURST_LIMIT;
        }
        // Lote no mesmo instante da janela anterior (flood continuado).
        dev.burstCount += readingCount;
        return dev.burstCount > BURST_LIMIT;
    }

    // ----------------------------------------------------------------------
    // Recompensa: penalidade baixa => recompensa maior. Agora apenas CALCULA e
    // PERSISTE (sem transfer, sem evento no caminho quente). So grava se mudar.
    // ----------------------------------------------------------------------
    function calculateReward(address device) public view returns (uint256) {
        Device storage dev = devices[device];
        if (!dev.exists || dev.blocked) {
            return 0;
        }
        uint256 penalty = dev.penaltyLevel + 1;
        return (REWARD_BASE + dev.reputation + (dev.accumulatedGas / 1000)) / penalty;
    }

    function _updateReward(Device storage dev) private returns (uint256 reward) {
        if (dev.blocked) {
            reward = 0;
        } else {
            uint256 penalty = dev.penaltyLevel + 1;
            reward = (REWARD_BASE + dev.reputation + (dev.accumulatedGas / 1000)) / penalty;
        }
        if (dev.reward != reward) {
            dev.reward = reward; // evita SSTORE redundante quando o valor nao muda
        }
        return reward;
    }

    // ----------------------------------------------------------------------
    // Getters (mesmas assinaturas da ABI anterior).
    // ----------------------------------------------------------------------
    function getDevice(address device)
        public
        view
        returns (
            string memory deviceId,
            string memory deviceType,
            uint transactionCount,
            uint unsafeOperations,
            uint penaltyLevel,
            uint reputation,
            uint accumulatedGas,
            bool blocked,
            uint reward
        )
    {
        Device storage d = devices[device];
        return (
            d.deviceId,
            d.deviceType,
            d.transactionCount,
            d.unsafeOperations,
            d.penaltyLevel,
            d.reputation,
            d.accumulatedGas,
            d.blocked,
            d.reward
        );
    }

    function isBlocked(address device) public view returns (bool) {
        return devices[device].blocked;
    }

    // Compatibilidade de ABI: o "historico" agora e agregado. getHistoryLength
    // devolve a QUANTIDADE de leituras; getHistoryEntry devolve a ULTIMA leitura.
    function getHistoryLength(address device) public view returns (uint256) {
        return devices[device].transactionCount;
    }

    function getHistoryEntry(address device, uint256 index)
        public
        view
        returns (uint256 temperature, uint256 timestamp, uint256 gasUsed)
    {
        Device storage d = devices[device];
        require(index < d.transactionCount, "Indice de historico invalido");
        return (d.lastTemperature, d.lastTimestamp, d.accumulatedGas);
    }

    /**
     * Estatisticas agregadas que substituem o historico completo (conforme o
     * enunciado): ultima leitura, ultimo timestamp, quantidade, media de
     * temperatura e frequencia media (leituras por segundo, x1000 para nao
     * perder precisao em inteiros).
     */
    function getDeviceStatistics(address device)
        public
        view
        returns (
            uint256 lastTemperature,
            uint256 lastTimestamp,
            uint256 readingCount,
            uint256 avgTemperature,
            uint256 avgFrequencyMilliHz
        )
    {
        Device storage d = devices[device];
        uint256 count = d.transactionCount;
        uint256 avg = count > 0 ? d.tempSum / count : 0;
        uint256 span = (d.lastTimestamp > d.firstTimestamp) ? (d.lastTimestamp - d.firstTimestamp) : 0;
        uint256 freq = span > 0 ? (count * 1000) / span : 0;
        return (d.lastTemperature, d.lastTimestamp, count, avg, freq);
    }

    function getTotalDevices() public view returns (uint256) {
        return deviceList.length;
    }

    /** Rastreabilidade: hash do ultimo lote e quantidade de lotes do dispositivo. */
    function getLastBatch(address device)
        public
        view
        returns (bytes32 lastBatchHash, uint256 batchCount)
    {
        Device storage d = devices[device];
        return (d.lastBatchHash, d.batchCount);
    }

    function getStats()
        public
        view
        returns (uint256 readings, uint256 approved, uint256 rejected, uint256 registeredDevices)
    {
        return (totalReadings, totalApproved, totalRejected, deviceList.length);
    }
}
