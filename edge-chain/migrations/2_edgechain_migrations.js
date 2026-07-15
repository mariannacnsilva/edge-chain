var EdgeChain = artifacts.require("./EdgeChain.sol");

module.exports = function(deployer, network) {
  if (network === "singlechain") {
    deployer.deploy(EdgeChain);
  }
};