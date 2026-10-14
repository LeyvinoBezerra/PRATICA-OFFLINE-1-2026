package discovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ServiceDiscoveryClient {
    private final InetSocketAddress directory;

    public ServiceDiscoveryClient(InetSocketAddress directory) {
        this.directory = directory;
    }

    public String calculate(String operation, double left, double right) throws IOException {
        // Descoberta e calculo sao saltos separados: o diretorio permanece fora do
        // caminho de dados depois de retornar o endpoint da calculadora.
        String discovery = request(directory, "GET|" + operation);
        String[] endpoint = discovery.split("\\|", -1);
        if (endpoint.length != 3 || !endpoint[0].equals("SERVER")) {
            throw new IOException("Descoberta de " + operation + " falhou: " + discovery);
        }

        int port;
        try {
            port = Integer.parseInt(endpoint[2]);
        } catch (NumberFormatException exception) {
            throw new IOException("Diretorio retornou uma porta invalida: " + discovery, exception);
        }
        String result = request(new InetSocketAddress(endpoint[1], port),
                "CALC|" + operation + "|" + left + "|" + right);
        if (result.startsWith("ERROR|")) {
            throw new IOException("Calculadora recusou a operacao: " + result.substring(6));
        }
        if (!result.startsWith("RESULT|")) {
            throw new IOException("Resposta invalida da calculadora: " + result);
        }
        return result.substring("RESULT|".length());
    }

    static String request(InetSocketAddress endpoint, String message) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(endpoint, 3000);
            socket.setSoTimeout(3000);
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter output = new PrintWriter(socket.getOutputStream(), true,
                         StandardCharsets.UTF_8)) {
                output.println(message);
                String response = input.readLine();
                if (response == null) {
                    throw new IOException("Conexao encerrada sem resposta");
                }
                return response;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 5) {
            throw new IllegalArgumentException("Uso: ServiceDiscoveryClient operacao "
                    + "numero1 numero2 [diretorio-host] [diretorio-porta]");
        }
        String operation = args[0];
        double left = Double.parseDouble(args[1]);
        double right = Double.parseDouble(args[2]);
        InetAddress directoryHost = args.length >= 4
                ? InetAddress.getByName(args[3]) : InetAddress.getLoopbackAddress();
        int directoryPort = args.length == 5 ? Integer.parseInt(args[4]) : 5000;

        ServiceDiscoveryClient client = new ServiceDiscoveryClient(
                new InetSocketAddress(directoryHost, directoryPort));
        System.out.println("Resultado: " + client.calculate(operation, left, right));
    }
}
