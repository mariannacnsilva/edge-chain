import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;

public class Client {

    public static void main(String[] args) {

        // Classe criada para que o cliente iot interaja com o contrato.É incluida no projeto onde se encontra o cliente java
        Web3j web3j = Web3j.build(new HttpService("HTTP://127.0.0.1:7545"));
        try {
            web3j.web3ClientVersion().send(); // Teste de conexão
            System.out.println("Conectado com sucesso!");
        } catch (Exception e) {
            System.out.println("Erro na conexão: " + e.getMessage());
        }
        
        Credentials credentials = Credentials.create("0x470b1384bec70a2aed460c8b54556e95ec8d712df392fc0af1856cb367593e4d");
        int version = 0;        

        System.out.println("Contract loading.");

        // Teste se o contrato existe
        String code;
        try {
            code = web3j.ethGetCode("0xE5dE808CDb89656AcC3aeCC3433310aE1a897D4A", DefaultBlockParameterName.LATEST).send().getCode();
            System.out.println("Código do contrato: " + code);
            if (code.equals("0x")) {
                System.out.println("ERRO: Contrato não existe neste endereço!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        HelloWorld helloWorldContract = HelloWorld.load(
            "0xE5dE808CDb89656AcC3aeCC3433310aE1a897D4A",
             web3j, 
             credentials, 
             BigInteger.valueOf(20_000_000_000L), 
             BigInteger.valueOf(300_000));
        System.out.println("Contract loaded.\n");

        EdgeChain edgeChainContract = EdgeChain.load(
            "0x47C3f901C221cB2D95a2BC773270D9faB75bE35E",
             web3j, 
             credentials, 
             BigInteger.valueOf(20_000_000_000L), 
             BigInteger.valueOf(300_000));

        try {

            InputStreamReader isr = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader (isr);
            TransactionReceipt transactionReceipt;
            String response = "";
            int numero = -1;

            while(true){

                numero = -1;
                response = "";

                System.out.print("Insira um número de 0 a 4: ");
                numero = Integer.parseInt (br.readLine());
                switch(numero){
                    case 0:{
                        response = hexToASCII(bytesToHex(helloWorldContract.hi().send()));
                        response = edgeChainContract.any_operation(BigInteger.valueOf(1)).sendAsync().get().getStatus();
                        System.out.println("0: Diga oi: " +response);
                        break;
                    }
                    case 1:{
                        System.out.println("1: Getting HelloNum: " +helloWorldContract.getHellonum().send());
                        break;
                    }
                    case 2:{
                        try {
                            transactionReceipt = helloWorldContract.hinofree().send();

                            System.out.println("Hash da transação: " + transactionReceipt.getTransactionHash());
                            System.out.println("Status: " + transactionReceipt.getStatus());
                            System.out.println("Gas usado: " + transactionReceipt.getGasUsed());
                        } catch (InterruptedException e) {
                            System.out.println("Erro: Transação foi interrompida");
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            System.out.println("Erro ao executar transação: " + e.getCause().getMessage());
                        }

                        break;
                    }
                    case 3:{
                        System.out.println("3: Getting version: " +helloWorldContract.getVersion().send());
                        break;
                    }
                    case 4:{
                        try {
                            transactionReceipt = helloWorldContract.setVersion(BigInteger.valueOf(7)).sendAsync().get();
                            
                            System.out.println("Transação enviada com sucesso!");
                            System.out.println("Hash: " + transactionReceipt.getTransactionHash());
                            System.out.println("Status: " + transactionReceipt.getStatus());
                        } catch (InterruptedException e) {
                            System.out.println("Erro: Transação foi interrompida");
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            System.out.println("Erro ao executar transação: " + e.getCause().getMessage());
                        }
                        break;
            }
                    case 5:{
                        System.out.println("5: getting version from firebase: " + sendGet());

                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + numero);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String bytesToHex(byte[] hashInBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String hexToASCII(String hexValue) {
        char extraZero = (char) Integer.parseInt("00", 16);
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < hexValue.length(); i += 2) {
            String str = hexValue.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString().replaceAll(String.valueOf(extraZero), "");
    }

    private static int sendGet() throws Exception {

        URL obj = new URL("https://learningfirebase-e409e.firebaseio.com/version.json");
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to Firebase");
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return Integer.parseInt(response.toString());
    }


}