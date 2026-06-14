// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract Bridge {
    
    address public owner;
    uint256 public batchCounter = 0;
    mapping(uint256 => bytes32) public batchStateHash;
    mapping(uint256 => uint256) public batchBlockHeight;
    mapping(uint256 => uint256) public batchTimestamp;
    mapping(uint256 => address) public batchSubmitter;
    mapping(uint256 => uint256) public batchTransactionCount;
    mapping(address => uint256) public ultimoBatchSubmitter;
    mapping(address => bool) public validadores;
    address public helloWorldMainChain;
    
    event BatchAncorado(
        uint256 indexed batchId,
        bytes32 indexed stateHash,
        uint256 blockHeight,
        address indexed submitter,
        uint256 transactionCount,
        uint256 timestamp
    );
    
    event ValidadorAdicionado(address indexed validador);
    event ValidadorRemovido(address indexed validador);
    
    modifier apenasOwner() {
        require(msg.sender == owner, "Apenas owner pode executar");
        _;
    }
    
    modifier validadorRequerido() {
        require(validadores[msg.sender] || msg.sender == owner, "Apenas validadores podem submeter batches");
        _;
    }
    
    constructor(address _helloWorldMainChain) {
        owner = msg.sender;
        helloWorldMainChain = _helloWorldMainChain;
        validadores[msg.sender] = true;
    }
    
    function adicionarValidador(address _validador) public apenasOwner {
        require(_validador != address(0), "Endereco invalido");
        require(!validadores[_validador], "Ja e validador");
        validadores[_validador] = true;
        emit ValidadorAdicionado(_validador);
    }
    
    function removerValidador(address _validador) public apenasOwner {
        require(validadores[_validador], "Nao e validador");
        require(_validador != owner, "Nao pode remover o owner");
        validadores[_validador] = false;
        emit ValidadorRemovido(_validador);
    }
    
    function setHelloWorldMainChain(address _newAddress) public apenasOwner {
        require(_newAddress != address(0), "Endereco invalido");
        helloWorldMainChain = _newAddress;
    }
    
    function submeterBatch(
        bytes32 _stateHash,
        uint256 _blockHeight,
        uint256 _transactionCount
    ) public validadorRequerido returns (uint256) {
        require(_stateHash != bytes32(0), "State hash nao pode ser vazio");
        require(_blockHeight > 0, "Block height deve ser positivo");
        require(_transactionCount > 0, "Transaction count deve ser positivo");
        
        uint256 batchId = batchCounter;
        batchStateHash[batchId] = _stateHash;
        batchBlockHeight[batchId] = _blockHeight;
        batchTimestamp[batchId] = block.timestamp;
        batchSubmitter[batchId] = msg.sender;
        batchTransactionCount[batchId] = _transactionCount;
        
        ultimoBatchSubmitter[msg.sender] = batchId;
        batchCounter++;
        
        emit BatchAncorado(
            batchId,
            _stateHash,
            _blockHeight,
            msg.sender,
            _transactionCount,
            block.timestamp
        );
        
        return batchId;
    }
    
    function submeterBatchComValidacao(
        bytes32 _stateHash,
        uint256 _blockHeight,
        uint256 _transactionCount,
        address[] memory _validadoresAcordados
    ) public returns (uint256) {
        require(_validadoresAcordados.length >= 2, "Pelo menos 2 validadores requeridos");
        
        uint256 validadoresContados = 0;
        for (uint256 i = 0; i < _validadoresAcordados.length; i++) {
            require(validadores[_validadoresAcordados[i]], "Um dos enderecos nao e validador");
            validadoresContados++;
        }
        
        require(validadoresContados >= 2, "Consenso insuficiente");
        return submeterBatch(_stateHash, _blockHeight, _transactionCount);
    }
    
    function obterBatch(uint256 _batchId) public view returns (
        bytes32 stateHash,
        uint256 blockHeight,
        uint256 timestamp,
        address submitter,
        uint256 transactionCount
    ) {
        require(_batchId < batchCounter, "Batch invalido");
        return (
            batchStateHash[_batchId],
            batchBlockHeight[_batchId],
            batchTimestamp[_batchId],
            batchSubmitter[_batchId],
            batchTransactionCount[_batchId]
        );
    }
    
    function getTotalBatches() public view returns (uint256) {
        return batchCounter;
    }
    
    function estaAncorado(bytes32 _stateHash) public view returns (bool) {
        for (uint256 i = 0; i < batchCounter; i++) {
            if (batchStateHash[i] == _stateHash) {
                return true;
            }
        }
        return false;
    }
    
    function getUltimoAnchorTimestamp() public view returns (uint256) {
        if (batchCounter == 0) return 0;
        return batchTimestamp[batchCounter - 1];
    }
    
    function tempoDesdeUltimoAnchor() public view returns (uint256) {
        if (batchCounter == 0) return 0;
        return block.timestamp - batchTimestamp[batchCounter - 1];
    }
    
    function validarBatch(
        uint256 _batchId,
        bytes32 _stateHash,
        uint256 _blockHeight
    ) public view returns (bool) {
        if (_batchId >= batchCounter) return false;
        return (batchStateHash[_batchId] == _stateHash && batchBlockHeight[_batchId] == _blockHeight);
    }
    
    function getEstatisticas() public view returns (
        uint256 totalBatches,
        uint256 ultimoTimestamp,
        uint256 totalTransacoes
    ) {
        totalBatches = batchCounter;
        if (batchCounter > 0) {
            ultimoTimestamp = batchTimestamp[batchCounter - 1];
            for (uint256 i = 0; i < batchCounter; i++) {
                totalTransacoes += batchTransactionCount[i];
            }
        }
        return (totalBatches, ultimoTimestamp, totalTransacoes);
    }
}
