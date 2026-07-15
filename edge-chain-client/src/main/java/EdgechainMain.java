import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.1.1.
 */
public class EdgechainMain extends Contract {
    private static final String BINARY = "6080604052348015600e575f5ffd5b505f80546001600160a01b031916331790556106288061002d5f395ff3fe608060405234801561000f575f5ffd5b50600436106100a6575f3560e01c8063b48d8bef1161006e578063b48d8bef146101e3578063b7fc3e51146101ec578063cde0a4f81461021c578063dd8fee1414610231578063ed23202914610244578063f8fb0ee41461024d575f5ffd5b8063352236b2146100aa57806369f302e3146100df5780637acd7f48146100f55780638da5cb5b1461019557806394367924146101bf575b5f5ffd5b6100bd6100b8366004610544565b610255565b6040805193151584529115156020840152908201526060015b60405180910390f35b6100e7605081565b6040519081526020016100d6565b61015a6040805160c0810182526004546001600160a01b03168082526005546020830181905260065493830184905260075460ff8082161515606086018190526101009092041615156080850181905260085460a09095018590529295919493909291565b604080516001600160a01b039097168752602087019590955293850192909252151560608401521515608083015260a082015260c0016100d6565b5f546101a7906001600160a01b031681565b6040516001600160a01b0390911681526020016100d6565b6001546101d390600160a01b900460ff1681565b60405190151581526020016100d6565b6100e760035481565b60045460055460065460075460085461015a946001600160a01b031693929160ff80821692610100909204169086565b61022f61022a36600461057a565b61049d565b005b6001546101a7906001600160a01b031681565b6100e760025481565b6002546100e7565b6001545f90819081906001600160a01b0316158061027d57506001546001600160a01b031633145b8061029157505f546001600160a01b031633145b6103015760405162461bcd60e51b815260206004820152603660248201527f4170656e6173206f20726567756c61646f72202873696465636861696e2920706044820152756f6465206578656375746172206f70657261636f657360501b60648201526084015b60405180910390fd5b5f5a600280549192505f610314836105ae565b919050555060508611156103a0576001805460ff60a01b1916600160a01b178155600380549195508594505f610349836105ae565b909155505060408051878152602081018790528415158183015290516001600160a01b038a16917f46183caa09bbafc5cd68a31e6cbbfa818b94948af602a7a38730d6aacbd4f246919081900360600190a26103b4565b6001805460ff60a01b191690555f93508392505b5a6103bf90826105c6565b6103cb906152086105df565b6040805160c0810182526001600160a01b038b1680825260208083018b90528284018a90528815156060808501829052891515608080870182905260a0909601889052600480546001600160a01b0319168617905560058e905560068d90556007805461ffff191661ff00199094169390931761010090910217909155600886905584518d81529182018c90529381018a90529283018490529294507fb5e34d5677b94eb262b1ae0dacf482ea342dde58689cfa6fededb4ef0cb97bed910160405180910390a2509450945094915050565b5f546001600160a01b031633146105075760405162461bcd60e51b815260206004820152602860248201527f4170656e6173206f776e657220706f646520636f6e66696775726172206f207260448201526732b3bab630b237b960c11b60648201526084016102f8565b600180546001600160a01b0319166001600160a01b0392909216919091179055565b80356001600160a01b038116811461053f575f5ffd5b919050565b5f5f5f5f60808587031215610557575f5ffd5b61056085610529565b966020860135965060408601359560600135945092505050565b5f6020828403121561058a575f5ffd5b61059382610529565b9392505050565b634e487b7160e01b5f52601160045260245ffd5b5f600182016105bf576105bf61059a565b5060010190565b818103818111156105d9576105d961059a565b92915050565b808201808211156105d9576105d961059a56fea2646970667358221220e8c659032ea2ba3274e695e3efbc727f92d0627cad5f36fa6b9e04dc06ffa1ea64736f6c63430008230033";

    public static final String FUNC_CRITICAL_TEMPERATURE = "CRITICAL_TEMPERATURE";

    public static final String FUNC_CRITICALALERT = "criticalAlert";

    public static final String FUNC_EXECUTETEMPERATUREOPERATION = "executeTemperatureOperation";

    public static final String FUNC_GETLASTEXECUTION = "getLastExecution";

    public static final String FUNC_GETTOTALOPERATIONS = "getTotalOperations";

    public static final String FUNC_LASTEXECUTION = "lastExecution";

    public static final String FUNC_OWNER = "owner";

    public static final String FUNC_REGULATOR = "regulator";

    public static final String FUNC_SETREGULATOR = "setRegulator";

    public static final String FUNC_TOTALCRITICALALERTS = "totalCriticalAlerts";

    public static final String FUNC_TOTALOPERATIONS = "totalOperations";

    public static final Event CRITICALALERT_EVENT = new Event("CriticalAlert", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}));
    ;

    public static final Event TEMPERATUREPROCESSED_EVENT = new Event("TemperatureProcessed", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    @Deprecated
    protected EdgechainMain(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected EdgechainMain(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected EdgechainMain(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected EdgechainMain(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<CriticalAlertEventResponse> getCriticalAlertEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(CRITICALALERT_EVENT, transactionReceipt);
        ArrayList<CriticalAlertEventResponse> responses = new ArrayList<CriticalAlertEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            CriticalAlertEventResponse typedResponse = new CriticalAlertEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.temperature = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.deviceShouldShutdown = (Boolean) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<CriticalAlertEventResponse> criticalAlertEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, CriticalAlertEventResponse>() {
            @Override
            public CriticalAlertEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(CRITICALALERT_EVENT, log);
                CriticalAlertEventResponse typedResponse = new CriticalAlertEventResponse();
                typedResponse.log = log;
                typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.temperature = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.deviceShouldShutdown = (Boolean) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<CriticalAlertEventResponse> criticalAlertEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(CRITICALALERT_EVENT));
        return criticalAlertEventFlowable(filter);
    }

    public List<TemperatureProcessedEventResponse> getTemperatureProcessedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(TEMPERATUREPROCESSED_EVENT, transactionReceipt);
        ArrayList<TemperatureProcessedEventResponse> responses = new ArrayList<TemperatureProcessedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            TemperatureProcessedEventResponse typedResponse = new TemperatureProcessedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.operationType = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.temperature = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.gasUsed = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<TemperatureProcessedEventResponse> temperatureProcessedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, TemperatureProcessedEventResponse>() {
            @Override
            public TemperatureProcessedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(TEMPERATUREPROCESSED_EVENT, log);
                TemperatureProcessedEventResponse typedResponse = new TemperatureProcessedEventResponse();
                typedResponse.log = log;
                typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.operationType = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.temperature = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.gasUsed = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<TemperatureProcessedEventResponse> temperatureProcessedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(TEMPERATUREPROCESSED_EVENT));
        return temperatureProcessedEventFlowable(filter);
    }

    public RemoteCall<TransactionReceipt> CRITICAL_TEMPERATURE() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CRITICAL_TEMPERATURE, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> criticalAlert() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CRITICALALERT, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> executeTemperatureOperation(String device, BigInteger operationType, BigInteger temperature, BigInteger timestamp) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_EXECUTETEMPERATUREOPERATION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(device), 
                new org.web3j.abi.datatypes.generated.Uint256(operationType), 
                new org.web3j.abi.datatypes.generated.Uint256(temperature), 
                new org.web3j.abi.datatypes.generated.Uint256(timestamp)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> getLastExecution() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETLASTEXECUTION, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> getTotalOperations() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETTOTALOPERATIONS, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> lastExecution() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_LASTEXECUTION, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> owner() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_OWNER, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> regulator() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REGULATOR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> setRegulator(String _regulator) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_SETREGULATOR, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(_regulator)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> totalCriticalAlerts() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TOTALCRITICALALERTS, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> totalOperations() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TOTALOPERATIONS, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static EdgechainMain load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new EdgechainMain(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static EdgechainMain load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new EdgechainMain(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static EdgechainMain load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new EdgechainMain(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static EdgechainMain load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new EdgechainMain(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<EdgechainMain> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(EdgechainMain.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<EdgechainMain> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(EdgechainMain.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<EdgechainMain> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(EdgechainMain.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<EdgechainMain> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(EdgechainMain.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class CriticalAlertEventResponse {
        public Log log;

        public String device;

        public BigInteger temperature;

        public BigInteger timestamp;

        public Boolean deviceShouldShutdown;
    }

    public static class TemperatureProcessedEventResponse {
        public Log log;

        public String device;

        public BigInteger operationType;

        public BigInteger temperature;

        public BigInteger timestamp;

        public BigInteger gasUsed;
    }
}
