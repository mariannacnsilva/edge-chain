var EdgeChain = artifacts.require("./EdgeChain.sol");

module.exports = function(deployer, network) {
  if (network === "sidechain") {
    deployer.deploy(EdgeChain);
  }
};