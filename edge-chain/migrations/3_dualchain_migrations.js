var EdgechainMain = artifacts.require("./EdgechainMain.sol");
var EdgechainRegulator = artifacts.require("./EdgechainRegulator.sol");

module.exports = function(deployer, network) {
  // Cada contrato vai para a SUA chain:
  //   - EdgechainMain      -> mainchain (porta 7545)
  //   - EdgechainRegulator -> sidechain (porta 8545)
  // No modo cross-chain o regulador fica com mainchainContract = address(0);
  // o encaminhamento a mainchain e feito off-chain pelo relayer Java.
  if (network === "mainchain") {
    deployer.deploy(EdgechainMain);
  } else if (network === "sidechain") {
    deployer.deploy(EdgechainRegulator);
  }
};
