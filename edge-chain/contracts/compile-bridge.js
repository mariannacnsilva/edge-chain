const solc = require('solc');
const fs = require('fs');
const path = require('path');

// Ler o arquivo Bridge.sol
const bridgePath = path.join(__dirname, 'Bridge.sol');
const sourceCode = fs.readFileSync(bridgePath, 'utf8');

// Configurar entrada para compilação
const input = {
  language: 'Solidity',
  sources: {
    'Bridge.sol': {
      content: sourceCode
    }
  },
  settings: {
    optimizer: {
      enabled: true,
      runs: 200
    },
    outputSelection: {
      '*': {
        '*': ['abi', 'evm.bytecode', 'evm.deployedBytecode']
      }
    }
  }
};

console.log('Compilando Bridge.sol...');

try {
  const output = JSON.parse(solc.compile(JSON.stringify(input)));

  // Verificar erros de compilação
  if (output.errors) {
    output.errors.forEach(error => {
      if (error.severity === 'error') {
        console.error('Erro de compilação:', error.message);
      } else {
        console.warn('Aviso:', error.message);
      }
    });
  }

  // Extrair ABI e bytecode
  if (output.contracts && output.contracts['Bridge.sol'] && output.contracts['Bridge.sol']['Bridge']) {
    const bridge = output.contracts['Bridge.sol']['Bridge'];
    
    // Salvar ABI
    fs.writeFileSync(path.join(__dirname, 'Bridge.abi'), JSON.stringify(bridge.abi, null, 2));
    console.log('✓ Bridge.abi gerado');
    
    // Salvar bytecode
    fs.writeFileSync(path.join(__dirname, 'Bridge.bin'), bridge.evm.bytecode.object);
    console.log('✓ Bridge.bin gerado');
    
    console.log('\n✓ Compilação concluída com sucesso!');
    console.log('Próximo passo: gerar wrapper Java com web3j');
  } else {
    console.error('Contrato não encontrado na compilação');
  }
} catch (error) {
  console.error('Erro na compilação:', error.message);
}
