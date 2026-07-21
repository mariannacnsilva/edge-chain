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
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
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
public class EdgechainRegulator extends Contract {
    private static final String BINARY = "6080604052348015600e575f5ffd5b50600380546001600160a01b031916331790556117188061002e5f395ff3fe608060405234801561000f575f5ffd5b50600436106100ef575f3560e01c80638da5cb5b11610093578063cba09cc811610063578063cba09cc814610270578063d82e396214610279578063dc61d1611461028c578063fbac395114610294575f5ffd5b80638da5cb5b146102005780639e6fb47214610213578063b14e52e81461021c578063c59d484714610244575f5ffd5b80633f28a9cc116100ce5780633f28a9cc1461017f57806346f12357146101aa57806373dc5aa1146101bd5780638cc9383e146101d2575f5ffd5b8062d55318146100f3578063072e1840146101245780632217432c1461013b575b5f5ffd5b61010661010136600461125b565b6102d2565b60405161011b999897969594939291906112a9565b60405180910390f35b61012d60075481565b60405190815260200161011b565b61014e6101493660046113a9565b610520565b60405161011b9594939291909415158552921515602085015290151560408401526060830152608082015260a00190565b600454610192906001600160a01b031681565b6040516001600160a01b03909116815260200161011b565b61012d6101b8366004611437565b610b6a565b6101d06101cb36600461125b565b610c26565b005b6101e56101e0366004611473565b610cb3565b6040805193845260208401929092529082015260600161011b565b600354610192906001600160a01b031681565b61012d60055481565b61012d61022a36600461125b565b6001600160a01b03165f9081526001602052604090205490565b60055460065460075460025460408051948552602085019390935291830152606082015260800161011b565b61012d60065481565b61012d61028736600461125b565b610d8d565b60025461012d565b6102c26102a236600461125b565b6001600160a01b03165f9081526020819052604090206008015460ff1690565b604051901515815260200161011b565b6060805f5f5f5f5f5f5f5f5f5f8c6001600160a01b03166001600160a01b031681526020019081526020015f20604051806101c00160405290815f8201805461031a9061149b565b80601f01602080910402602001604051908101604052809291908181526020018280546103469061149b565b80156103915780601f1061036857610100808354040283529160200191610391565b820191905f5260205f20905b81548152906001019060200180831161037457829003601f168201915b505050918352505060018201546001600160a01b031660208201526002820180546040909201916103c19061149b565b80601f01602080910402602001604051908101604052809291908181526020018280546103ed9061149b565b80156104385780601f1061040f57610100808354040283529160200191610438565b820191905f5260205f20905b81548152906001019060200180831161041b57829003601f168201915b505050505081526020016003820154815260200160048201548152602001600582015481526020016006820154815260200160078201548152602001600882015f9054906101000a900460ff161515151581526020016008820160019054906101000a900460ff1615151515815260200160098201548152602001600a8201548152602001600b8201548152602001600c820154815250509050805f01518160400151826060015183608001518460a001518560c001518660e00151876101000151886101400151995099509950995099509950995099509950509193959799909294969850565b5f5f5f5f5f5f5a600580549192505f610538836114e7565b9091555050335f90815260208190526040902060080154610100900460ff1661072f57604080516101c0810182528c81523360208083018290528284018e90525f606084018190526080840181905260a08401819052606460c085015260e08401819052610100840181905260016101208501526101408401819052610160840181905261018084018190526101a0840181905291825281905291909120815181906105e49082611556565b5060208201516001820180546001600160a01b0319166001600160a01b039092169190911790556040820151600282019061061f9082611556565b50606082015160038201556080820151600482015560a0820151600582015560c0820151600682015560e082015160078201556101008083015160088301805461012086015161ffff1990911692151561ff001916929092179115159092021790556101408201516009820155610160820151600a820155610180820151600b8201556101a090910151600c90910155600280546001810182555f919091527f405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ace018054336001600160a01b031990911681179091556040517f25bec5442671a5d594ede63d811665f791f5152b85d1ca34a8c1630a11af352a90610726908e908e90611611565b60405180910390a25b335f908152602081905260409020600881015460ff16156108205780600c015488106107a35760088101805460ff191690556005808201555f600a8201819055600b820181905560405133917f9b6e82290c1fccb109425e0910e6ffe5c92c73af8c3d38f1e4be6ed42975847991a2610820565b60078054905f6107b2836114e7565b9091555050604080518a8152602081018a90525f818301819052606082018190526080820152905133917f3de3ae0054e03beb63b8fddcb4529f4dfa8adc2582440f9c48f638df0ab93e81919081900360a00190a2600901545f96508695508594508493509150610b5d9050565b600381018054905f610831836114e7565b91905055505f5a6108429084611635565b61084e90615208611648565b905061085c338b8b84610fd8565b5f610867838b6110ff565b905080156109c0576001836005015f8282546108839190611648565b925050819055506001836004015f82825461089e9190611648565b90915550506006830154600a116108ce57600a836006015f8282546108c39190611635565b909155506108d59050565b5f60068401555b60058360050154111561093c5760088301805460ff191660011790556108fc603c8b611648565b600c840155600583015460405190815233907fc629157caa751a27df4e3eb3bd9bbfa90ef0d97d8672113dd54912f8c976e06f9060200160405180910390a25b60078054905f61094b836114e7565b919050555061095983611173565b604080518d8152602081018d90525f818301819052606082018190526080820152905191965033917f3de3ae0054e03beb63b8fddcb4529f4dfa8adc2582440f9c48f638df0ab93e819181900360a00190a25f5f5f5f985098509850985050505050610b5d565b6005830154156109e4576001836005015f8282546109de9190611635565b90915550505b6001836006015f8282546109f89190611648565b90915550506004545f906001600160a01b031615610a985760048054604051631a911b5960e11b81523392810192909252602482018f9052604482018e9052606482018d90526001600160a01b03169063352236b2906084016060604051808303815f875af1925050508015610a8b575060408051601f3d908101601f19168201909252610a889181019061165b565b60015b15610a9857919a50985090505b60068054905f610aa7836114e7565b91905055505f5a610ab89087611635565b610ac490615208611648565b9050610ad08282611648565b856007015f828254610ae29190611648565b90915550610af1905085611173565b9650610afd8282611648565b604080518f8152602081018f90526001818301528c151560608201528b15156080820152905191995033917f3de3ae0054e03beb63b8fddcb4529f4dfa8adc2582440f9c48f638df0ab93e819181900360a00190a260019a505050505050505b9550955095509550959050565b6001600160a01b0383165f90815260208190526040812060080154610100900460ff16610bde5760405162461bcd60e51b815260206004820152601a60248201527f446973706f73697469766f206e616f206361646173747261646f00000000000060448201526064015b60405180910390fd5b6001600160a01b0384165f9081526020819052604090208315610c145782816007015f828254610c0e9190611648565b90915550505b610c1d81611173565b95945050505050565b6003546001600160a01b03163314610c915760405162461bcd60e51b815260206004820152602860248201527f4170656e6173206f776e657220706f646520636f6e666967757261722061206d60448201526730b4b731b430b4b760c11b6064820152608401610bd5565b600480546001600160a01b0319166001600160a01b0392909216919091179055565b6001600160a01b0382165f90815260016020526040812054819081908410610d1d5760405162461bcd60e51b815260206004820152601c60248201527f496e6469636520646520686973746f7269636f20696e76616c69646f000000006044820152606401610bd5565b6001600160a01b0385165f908152600160205260408120805486908110610d4657610d4661169b565b5f9182526020918290206040805160608101825260039390930290910180548084526001820154948401859052600290910154929091018290529891975095509350505050565b6001600160a01b0381165f9081526020819052604080822081516101c0810190925280548392919082908290610dc29061149b565b80601f0160208091040260200160405190810160405280929190818152602001828054610dee9061149b565b8015610e395780601f10610e1057610100808354040283529160200191610e39565b820191905f5260205f20905b815481529060010190602001808311610e1c57829003601f168201915b505050918352505060018201546001600160a01b03166020820152600282018054604090920191610e699061149b565b80601f0160208091040260200160405190810160405280929190818152602001828054610e959061149b565b8015610ee05780601f10610eb757610100808354040283529160200191610ee0565b820191905f5260205f20905b815481529060010190602001808311610ec357829003601f168201915b505050918352505060038201546020820152600482015460408201526005820154606082015260068201546080820152600782015460a0820152600882015460ff808216151560c08401526101009182900416151560e0830152600983015490820152600a82015461012080830191909152600b830154610140830152600c90920154610160909101528101519091501580610f7e57508061010001515b15610f8b57505f92915050565b5f8160a001516001610f9d9190611648565b90505f816103e88460e00151610fb391906116af565b60c0850151610fc4906103e8611648565b610fce9190611648565b610c1d91906116af565b6001600160a01b0384165f90815260016020818152604080842081516060810183528881528084018881529281018781528254808701845583885294909620905160039094020192835590519282019290925591516002909201919091558054603210156110f8575f5b815461105090600190611635565b8110156110c35781611063826001611648565b815481106110735761107361169b565b905f5260205f2090600302018282815481106110915761109161169b565b5f9182526020909120825460039092020190815560018083015481830155600292830154929091019190915501611042565b50808054806110d4576110d46116ce565b5f8281526020812060035f1990930192830201818155600181018290556002015590555b5050505050565b5f82600a01545f1480611115575082600a015482105b806111305750603c83600a01548361112d9190611635565b10155b1561114a5750600a82018190556001600b8301555f61116d565b600183600b015f82825461115e9190611648565b909155505050600b8201546028105b92915050565b5f5f826005015460016111869190611648565b600884015490915060ff161561119e575f91506111d9565b806103e884600701546111b191906116af565b60068501546111c2906103e8611648565b6111cc9190611648565b6111d691906116af565b91505b600983018290556001830154600684015460058501546040805186815260208101939093528201526001600160a01b03909116907f2154de89d5bc468103866cc236e52e0405df9bd8cf3888bb159579a365f51a089060600160405180910390a250919050565b80356001600160a01b0381168114611256575f5ffd5b919050565b5f6020828403121561126b575f5ffd5b61127482611240565b9392505050565b5f81518084528060208401602086015e5f602082860101526020601f19601f83011685010191505092915050565b61012081525f6112bd61012083018c61127b565b82810360208401526112cf818c61127b565b604084019a909a5250506060810196909652608086019490945260a085019290925260c0840152151560e08301526101009091015292915050565b634e487b7160e01b5f52604160045260245ffd5b5f82601f83011261132d575f5ffd5b813567ffffffffffffffff8111156113475761134761130a565b604051601f8201601f19908116603f0116810167ffffffffffffffff811182821017156113765761137661130a565b60405281815283820160200185101561138d575f5ffd5b816020850160208301375f918101602001919091529392505050565b5f5f5f5f5f60a086880312156113bd575f5ffd5b853567ffffffffffffffff8111156113d3575f5ffd5b6113df8882890161131e565b955050602086013567ffffffffffffffff8111156113fb575f5ffd5b6114078882890161131e565b959895975050505060408401359360608101359360809091013592509050565b8015158114611434575f5ffd5b50565b5f5f5f60608486031215611449575f5ffd5b61145284611240565b9250602084013561146281611427565b929592945050506040919091013590565b5f5f60408385031215611484575f5ffd5b61148d83611240565b946020939093013593505050565b600181811c908216806114af57607f821691505b6020821081036114cd57634e487b7160e01b5f52602260045260245ffd5b50919050565b634e487b7160e01b5f52601160045260245ffd5b5f600182016114f8576114f86114d3565b5060010190565b601f821115611551578282111561155157805f5260205f20601f840160051c602085101561152a57505f5b90810190601f840160051c035f5b8181101561154d575f83820155600101611538565b5050505b505050565b815167ffffffffffffffff8111156115705761157061130a565b6115848161157e845461149b565b846114ff565b6020601f8211600181146115b6575f831561159f5750848201515b5f19600385901b1c1916600184901b1784556110f8565b5f84815260208120601f198516915b828110156115e557878501518255602094850194600190920191016115c5565b508482101561160257868401515f19600387901b60f8161c191681555b50505050600190811b01905550565b604081525f611623604083018561127b565b8281036020840152610c1d818561127b565b8181038181111561116d5761116d6114d3565b8082018082111561116d5761116d6114d3565b5f5f5f6060848603121561166d575f5ffd5b835161167881611427565b602085015190935061168981611427565b80925050604084015190509250925092565b634e487b7160e01b5f52603260045260245ffd5b5f826116c957634e487b7160e01b5f52601260045260245ffd5b500490565b634e487b7160e01b5f52603160045260245ffdfea2646970667358221220414a126498e50ae04fd0ed647a8714314f3facfa8ab7d5a198278df45726102364736f6c63430008230033";

    public static final String FUNC_CALCULATEREWARD = "calculateReward";

    public static final String FUNC_GETDEVICE = "getDevice";

    public static final String FUNC_GETHISTORYENTRY = "getHistoryEntry";

    public static final String FUNC_GETHISTORYLENGTH = "getHistoryLength";

    public static final String FUNC_GETSTATS = "getStats";

    public static final String FUNC_GETTOTALDEVICES = "getTotalDevices";

    public static final String FUNC_ISBLOCKED = "isBlocked";

    public static final String FUNC_MAINCHAINCONTRACT = "mainchainContract";

    public static final String FUNC_OWNER = "owner";

    public static final String FUNC_REGISTERTEMPERATUREREADING = "registerTemperatureReading";

    public static final String FUNC_REGISTERBATCH = "registerBatch";

    public static final String FUNC_SETMAINCHAINCONTRACT = "setMainchainContract";

    public static final String FUNC_TOTALAPPROVED = "totalApproved";

    public static final String FUNC_TOTALREADINGS = "totalReadings";

    public static final String FUNC_TOTALREJECTED = "totalRejected";

    public static final String FUNC_UPDATEEXECUTIONCOST = "updateExecutionCost";

    public static final Event DEVICEBLOCKED_EVENT = new Event("DeviceBlocked", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event DEVICEREGISTERED_EVENT = new Event("DeviceRegistered", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Utf8String>() {}, new TypeReference<Utf8String>() {}));
    ;

    public static final Event DEVICEUNBLOCKED_EVENT = new Event("DeviceUnblocked", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}));
    ;

    public static final Event READINGVALIDATED_EVENT = new Event("ReadingValidated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}, new TypeReference<Bool>() {}, new TypeReference<Bool>() {}));
    ;

    public static final Event REWARDUPDATED_EVENT = new Event("RewardUpdated",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event BATCHVALIDATED_EVENT = new Event("BatchValidated",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}));
    ;

    @Deprecated
    protected EdgechainRegulator(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected EdgechainRegulator(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected EdgechainRegulator(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected EdgechainRegulator(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<DeviceBlockedEventResponse> getDeviceBlockedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DEVICEBLOCKED_EVENT, transactionReceipt);
        ArrayList<DeviceBlockedEventResponse> responses = new ArrayList<DeviceBlockedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DeviceBlockedEventResponse typedResponse = new DeviceBlockedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.penaltyLevel = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<DeviceBlockedEventResponse> deviceBlockedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, DeviceBlockedEventResponse>() {
            @Override
            public DeviceBlockedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(DEVICEBLOCKED_EVENT, log);
                DeviceBlockedEventResponse typedResponse = new DeviceBlockedEventResponse();
                typedResponse.log = log;
                typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.penaltyLevel = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DeviceBlockedEventResponse> deviceBlockedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEVICEBLOCKED_EVENT));
        return deviceBlockedEventFlowable(filter);
    }

    public List<DeviceRegisteredEventResponse> getDeviceRegisteredEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DEVICEREGISTERED_EVENT, transactionReceipt);
        ArrayList<DeviceRegisteredEventResponse> responses = new ArrayList<DeviceRegisteredEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DeviceRegisteredEventResponse typedResponse = new DeviceRegisteredEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.deviceId = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.deviceType = (String) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<DeviceRegisteredEventResponse> deviceRegisteredEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, DeviceRegisteredEventResponse>() {
            @Override
            public DeviceRegisteredEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(DEVICEREGISTERED_EVENT, log);
                DeviceRegisteredEventResponse typedResponse = new DeviceRegisteredEventResponse();
                typedResponse.log = log;
                typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.deviceId = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.deviceType = (String) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DeviceRegisteredEventResponse> deviceRegisteredEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEVICEREGISTERED_EVENT));
        return deviceRegisteredEventFlowable(filter);
    }

    public List<DeviceUnblockedEventResponse> getDeviceUnblockedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DEVICEUNBLOCKED_EVENT, transactionReceipt);
        ArrayList<DeviceUnblockedEventResponse> responses = new ArrayList<DeviceUnblockedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DeviceUnblockedEventResponse typedResponse = new DeviceUnblockedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<DeviceUnblockedEventResponse> deviceUnblockedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, DeviceUnblockedEventResponse>() {
            @Override
            public DeviceUnblockedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(DEVICEUNBLOCKED_EVENT, log);
                DeviceUnblockedEventResponse typedResponse = new DeviceUnblockedEventResponse();
                typedResponse.log = log;
                typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DeviceUnblockedEventResponse> deviceUnblockedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEVICEUNBLOCKED_EVENT));
        return deviceUnblockedEventFlowable(filter);
    }

    public List<ReadingValidatedEventResponse> getReadingValidatedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(READINGVALIDATED_EVENT, transactionReceipt);
        ArrayList<ReadingValidatedEventResponse> responses = new ArrayList<ReadingValidatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ReadingValidatedEventResponse typedResponse = new ReadingValidatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.temperature = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.approved = (Boolean) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.temperatureCritical = (Boolean) eventValues.getNonIndexedValues().get(3).getValue();
            typedResponse.deviceShouldShutdown = (Boolean) eventValues.getNonIndexedValues().get(4).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<ReadingValidatedEventResponse> readingValidatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, ReadingValidatedEventResponse>() {
            @Override
            public ReadingValidatedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(READINGVALIDATED_EVENT, log);
                ReadingValidatedEventResponse typedResponse = new ReadingValidatedEventResponse();
                typedResponse.log = log;
                typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.temperature = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.approved = (Boolean) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.temperatureCritical = (Boolean) eventValues.getNonIndexedValues().get(3).getValue();
                typedResponse.deviceShouldShutdown = (Boolean) eventValues.getNonIndexedValues().get(4).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<ReadingValidatedEventResponse> readingValidatedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(READINGVALIDATED_EVENT));
        return readingValidatedEventFlowable(filter);
    }

    public List<RewardUpdatedEventResponse> getRewardUpdatedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(REWARDUPDATED_EVENT, transactionReceipt);
        ArrayList<RewardUpdatedEventResponse> responses = new ArrayList<RewardUpdatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RewardUpdatedEventResponse typedResponse = new RewardUpdatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.reward = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.reputation = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.penaltyLevel = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<RewardUpdatedEventResponse> rewardUpdatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, RewardUpdatedEventResponse>() {
            @Override
            public RewardUpdatedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(REWARDUPDATED_EVENT, log);
                RewardUpdatedEventResponse typedResponse = new RewardUpdatedEventResponse();
                typedResponse.log = log;
                typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.reward = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.reputation = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.penaltyLevel = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<RewardUpdatedEventResponse> rewardUpdatedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(REWARDUPDATED_EVENT));
        return rewardUpdatedEventFlowable(filter);
    }

    public List<BatchValidatedEventResponse> getBatchValidatedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(BATCHVALIDATED_EVENT, transactionReceipt);
        ArrayList<BatchValidatedEventResponse> responses = new ArrayList<BatchValidatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            BatchValidatedEventResponse typedResponse = new BatchValidatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.device = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.batchHash = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.readingCount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.avgTemperature = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.approved = (Boolean) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public RemoteCall<TransactionReceipt> calculateReward(String device) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CALCULATEREWARD, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(device)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> getDevice(String device) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETDEVICE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(device)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> getHistoryEntry(String device, BigInteger index) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETHISTORYENTRY, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(device), 
                new org.web3j.abi.datatypes.generated.Uint256(index)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> getHistoryLength(String device) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETHISTORYLENGTH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(device)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> getStats() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETSTATS, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> getTotalDevices() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETTOTALDEVICES, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> isBlocked(String device) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_ISBLOCKED, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(device)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> mainchainContract() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_MAINCHAINCONTRACT, 
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

    public RemoteCall<TransactionReceipt> registerTemperatureReading(String deviceId, String deviceType, BigInteger operationType, BigInteger temperature, BigInteger timestamp) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REGISTERTEMPERATUREREADING, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(deviceId), 
                new org.web3j.abi.datatypes.Utf8String(deviceType), 
                new org.web3j.abi.datatypes.generated.Uint256(operationType), 
                new org.web3j.abi.datatypes.generated.Uint256(temperature), 
                new org.web3j.abi.datatypes.generated.Uint256(timestamp)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> registerBatch(String deviceId, String deviceType, byte[] batchHash, BigInteger readingCount, BigInteger maxTemperature, BigInteger avgTemperature, BigInteger firstTimestamp, BigInteger lastTimestamp) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REGISTERBATCH,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(deviceId),
                new org.web3j.abi.datatypes.Utf8String(deviceType),
                new org.web3j.abi.datatypes.generated.Bytes32(batchHash),
                new org.web3j.abi.datatypes.generated.Uint256(readingCount),
                new org.web3j.abi.datatypes.generated.Uint256(maxTemperature),
                new org.web3j.abi.datatypes.generated.Uint256(avgTemperature),
                new org.web3j.abi.datatypes.generated.Uint256(firstTimestamp),
                new org.web3j.abi.datatypes.generated.Uint256(lastTimestamp)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> setMainchainContract(String _mainchain) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_SETMAINCHAINCONTRACT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(_mainchain)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> totalApproved() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TOTALAPPROVED, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> totalReadings() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TOTALREADINGS, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> totalRejected() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TOTALREJECTED, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> updateExecutionCost(String device, Boolean success, BigInteger mainGasUsed) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_UPDATEEXECUTIONCOST, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(device), 
                new org.web3j.abi.datatypes.Bool(success), 
                new org.web3j.abi.datatypes.generated.Uint256(mainGasUsed)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static EdgechainRegulator load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new EdgechainRegulator(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static EdgechainRegulator load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new EdgechainRegulator(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static EdgechainRegulator load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new EdgechainRegulator(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static EdgechainRegulator load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new EdgechainRegulator(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<EdgechainRegulator> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(EdgechainRegulator.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<EdgechainRegulator> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(EdgechainRegulator.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<EdgechainRegulator> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(EdgechainRegulator.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<EdgechainRegulator> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(EdgechainRegulator.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class DeviceBlockedEventResponse {
        public Log log;

        public String device;

        public BigInteger penaltyLevel;
    }

    public static class DeviceRegisteredEventResponse {
        public Log log;

        public String device;

        public String deviceId;

        public String deviceType;
    }

    public static class DeviceUnblockedEventResponse {
        public Log log;

        public String device;
    }

    public static class ReadingValidatedEventResponse {
        public Log log;

        public String device;

        public BigInteger temperature;

        public BigInteger timestamp;

        public Boolean approved;

        public Boolean temperatureCritical;

        public Boolean deviceShouldShutdown;
    }

    public static class RewardUpdatedEventResponse {
        public Log log;

        public String device;

        public BigInteger reward;

        public BigInteger reputation;

        public BigInteger penaltyLevel;
    }

    public static class BatchValidatedEventResponse {
        public Log log;

        public String device;

        public byte[] batchHash;

        public BigInteger readingCount;

        public BigInteger avgTemperature;

        public Boolean approved;
    }
}
