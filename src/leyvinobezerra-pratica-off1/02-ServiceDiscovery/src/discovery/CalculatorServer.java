package discovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CalculatorServer implements Runnable, AutoCloseable {
    // Um conjunto fixo centraliza as capacidades publicadas e evita divergencia entre
    // o que o servidor aceita e o que registra no diretorio.
    private static final List<String> OPERATIONS =
            List.of("soma", "subtracao", "multiplicacao", "divisao");

    private final String name;
    private final String advertiseHost;
    private final InetSocketAddress directory;
    private final ServerSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public CalculatorServer(String name, int port, InetAddress bindAddress,
                            String advertiseHost, InetSocketAddress directory) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome da calculadora nao pode ser vazio");
        }
        if (advertiseHost == null || advertiseHost.isBlank()) {
            throw new IllegalArgumentException("O endereco anunciado nao pode ser vazio");
        }
        this.name = name;
        this.advertiseHost = advertiseHost;
        this.directory = directory;
        socket = new ServerSocket();
        socket.bind(new InetSocketAddress(bindAddress, port));
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    public void run() {
        try {
            registerServices();
            System.out.println(name + " ouvindo em " + socket.getLocalSocketAddress());
            while (running.get()) {
                try {
                    Socket client = socket.accept();
                    startHandler(client);
                } catch (IOException exception) {
                    if (running.get()) {
                        System.err.println(name + ": " + exception.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            if (running.get()) {
                System.err.println(name + ": falha ao registrar no diretorio: "
                        + exception.getMessage());
            }
        }
    }

    private void registerServices() throws IOException {
        // O servidor anuncia o endpoint alcancavel pelo cliente antes de aceitar trabalho,
        // para que o diretorio nunca distribua uma calculadora ainda nao inicializada.
        for (String operation : OPERATIONS) {
            String response = discovery.ServiceDiscoveryClient.request(directory,
                    "REGISTER|" + operation + "|" + advertiseHost + "|" + getPort());
            if (!response.equals("OK|registrado")) {
                throw new IOException("Registro de " + operation + " recusado: " + response);
            }
        }
    }

    private void startHandler(Socket client) {
        // Isola conexoes para que uma requisicao nao serialize o atendimento da calculadora.
        Thread thread = new Thread(() -> handle(client), name + "-client");
        thread.setDaemon(true);
        thread.start();
    }

    private void handle(Socket client) {
        try (client;
             BufferedReader input = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(client.getOutputStream(), true,
                     StandardCharsets.UTF_8)) {
            String line = input.readLine();
            if (line == null) {
                output.println("ERROR|requisicao invalida");
                return;
            }
            String[] fields = line.split("\\|", -1);
            if (fields.length != 4 || !fields[0].equals("CALC")) {
                output.println("ERROR|requisicao invalida");
                return;
            }

            try {
                double left = Double.parseDouble(fields[2]);
                double right = Double.parseDouble(fields[3]);
                double result = calculate(fields[1], left, right);
                output.println("RESULT|" + result);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                output.println("ERROR|" + exception.getMessage());
            }
        } catch (IOException exception) {
            System.err.println(name + ": falha na conexao: " + exception.getMessage());
        }
    }

    private static double calculate(String operation, double left, double right) {
        return switch (operation) {
            case "soma" -> left + right;
            case "subtracao" -> left - right;
            case "multiplicacao" -> left * right;
            case "divisao" -> {
                if (right == 0) {
                    throw new ArithmeticException("divisao por zero");
                }
                yield left / right;
            }
            default -> throw new IllegalArgumentException("operacao desconhecida");
        };
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        socket.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 6) {
            throw new IllegalArgumentException("Uso: CalculatorServer [nome] [porta] "
                    + "[diretorio-host] [diretorio-porta] [bind] [host-anunciado]");
        }
        String name = args.length >= 1 ? args[0] : "Calculadora";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 0;
        InetAddress directoryHost = args.length >= 3
                ? InetAddress.getByName(args[2]) : InetAddress.getLoopbackAddress();
        int directoryPort = args.length >= 4 ? Integer.parseInt(args[3]) : 5000;
        InetAddress bindAddress = args.length >= 5
                ? InetAddress.getByName(args[4]) : InetAddress.getLoopbackAddress();
        String advertiseHost = args.length >= 6
                ? args[5] : InetAddress.getLoopbackAddress().getHostAddress();
        InetSocketAddress directory = new InetSocketAddress(directoryHost, directoryPort);
        try (CalculatorServer server = new CalculatorServer(
                name, port, bindAddress, advertiseHost, directory)) {
            server.run();
        }
    }
}
