const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const contractsDir = __dirname;
const web3jPath = path.join(contractsDir, '../../web3j-4.1.1/bin/web3j.bat');
const binFile = path.join(contractsDir, 'Bridge.bin');
const abiFile = path.join(contractsDir, 'Bridge.abi');
const outputDir = path.join(contractsDir, 'com/edge/chain');

// Criar diretório de saída se não existir
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

try {
    console.log('Gerando wrapper Java para Bridge...');
    console.log('web3j path:', web3jPath);
    console.log('Output dir:', outputDir);
    
    const cmd = `"${web3jPath}" solidity generate -b "${binFile}" -a "${abiFile}" -o "${outputDir}" -p com.edge.chain`;
    console.log('Executando:', cmd);
    
    const output = execSync(cmd, { 
        encoding: 'utf8',
        cwd: contractsDir 
    });
    
    console.log(output);
    console.log('\n✓ Wrapper Java Bridge gerado com sucesso!');
    console.log('Arquivo gerado em: ' + outputDir + '/Bridge.java');
    
} catch (error) {
    console.error('Erro ao gerar wrapper:', error.message);
    console.error('Detalhes:', error.stderr || error);
}
