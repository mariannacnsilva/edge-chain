var Bridge = artifacts.require("./Bridge.sol");

module.exports = function(deployer, network) {
  if (network === "mainchain") {
    deployer.deploy(Bridge);
  }
};