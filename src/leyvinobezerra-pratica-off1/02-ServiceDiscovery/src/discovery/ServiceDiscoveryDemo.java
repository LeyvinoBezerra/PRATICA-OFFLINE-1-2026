package discovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServiceDiscoveryDemo {
    private ServiceDiscoveryDemo() {
    }

    public static void main(String[] args) throws Exception {
        try (DirectoryServer directory = new DirectoryServer();
             CalculatorServer serverA = new CalculatorServer("Calculadora-A");
             CalculatorServer serverB = new CalculatorServer("Calculadora-B");
             CalculatorServer serverC = new CalculatorServer("Calculadora-C")) {
            start(directory, "diretorio");
            start(serverA, "calculadora-a");
            start(serverB, "calculadora-b");
            start(serverC, "calculadora-c");
            Thread.sleep(100);

            for (CalculatorServer server : List.of(serverA, serverB, serverC)) {
                for (String operation : List.of("soma", "subtracao", "multiplicacao", "divisao")) {
                    request(directory.port(), "REGISTER|" + operation + "|127.0.0.1|"
                            + server.port());
                }
            }

            Client[] clients = {
                    new Client("Cliente 1", directory.port()),
                    new Client("Cliente 2", directory.port()),
                    new Client("Cliente 3", directory.port())
            };
            clients[0].calculate("soma", 8, 5);
            clients[1].calculate("soma", 10, 3);
            clients[2].calculate("soma", 12, 4);
            clients[1].calculate("multiplicacao", 7, 6);
            clients[2].calculate("divisao", 20, 4);
        }
    }

    private static Thread start(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String request(int port, String message) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             BufferedReader input = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true,
                     StandardCharsets.UTF_8)) {
            socket.setSoTimeout(3000);
            output.println(message);
            String response = input.readLine();
            if (response == null) {
                throw new IOException("Conexao encerrada sem resposta");
            }
            return response;
        }
    }

    private static final class DirectoryServer implements Runnable, AutoCloseable {
        private final ServerSocket socket;
        private final Map<String, List<Endpoint>> services = new ConcurrentHashMap<>();
        private final Map<String, Integer> nextServer = new ConcurrentHashMap<>();
        private final AtomicBoolean running = new AtomicBoolean(true);

        private DirectoryServer() throws IOException {
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        }

        private int port() {
            return socket.getLocalPort();
        }

        @Override
        public void run() {
            while (running.get()) {
                try {
                    Socket client = socket.accept();
                    start(() -> handle(client), "directory-client");
                } catch (IOException exception) {
                    if (running.get()) {
                        System.err.println("Diretorio: " + exception.getMessage());
                    }
                }
            }
        }

        private void handle(Socket client) {
            try (client;
                 BufferedReader input = new BufferedReader(new InputStreamReader(
                         client.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter output = new PrintWriter(client.getOutputStream(), true,
                         StandardCharsets.UTF_8)) {
                String line = input.readLine();
                if (line == null) {
                    output.println("ERROR|requisicao vazia");
                    return;
                }
                String[] fields = line.split("\\|", -1);
                if (fields.length == 4 && fields[0].equals("REGISTER")) {
                    int port;
                    try {
                        port = Integer.parseInt(fields[3]);
                    } catch (NumberFormatException exception) {
                        output.println("ERROR|porta invalida");
                        return;
                    }
                    if (fields[1].isBlank() || port < 1 || port > 65535) {
                        output.println("ERROR|registro invalido");
                        return;
                    }
                    Endpoint endpoint = new Endpoint(fields[2], port);
                    services.compute(fields[1], (key, current) -> {
                        List<Endpoint> endpoints = current == null
                                ? new ArrayList<>() : new ArrayList<>(current);
                        if (!endpoints.contains(endpoint)) {
                            endpoints.add(endpoint);
                        }
                        return endpoints;
                    });
                    output.println("OK|registrado");
                } else if (fields.length == 2 && fields[0].equals("GET")) {
                    List<Endpoint> endpoints = services.get(fields[1]);
                    if (endpoints == null || endpoints.isEmpty()) {
                        output.println("ERROR|servico indisponivel");
                        return;
                    }
                    int index = Math.floorMod(nextServer.merge(fields[1], 1, Integer::sum) - 1,
                            endpoints.size());
                    Endpoint endpoint = endpoints.get(index);
                    output.println("SERVER|" + endpoint.host() + "|" + endpoint.port());
                } else {
                    output.println("ERROR|requisicao invalida");
                }
            } catch (IOException exception) {
                System.err.println("Diretorio: requisicao invalida: " + exception.getMessage());
            }
        }

        @Override
        public void close() throws IOException {
            running.set(false);
            socket.close();
        }
    }

    private static final class CalculatorServer implements Runnable, AutoCloseable {
        private final String name;
        private final ServerSocket socket;
        private final AtomicBoolean running = new AtomicBoolean(true);

        private CalculatorServer(String name) throws IOException {
            this.name = name;
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        }

        private int port() {
            return socket.getLocalPort();
        }

        @Override
        public void run() {
            while (running.get()) {
                try {
                    Socket client = socket.accept();
                    start(() -> handle(client), name + "-client");
                } catch (IOException exception) {
                    if (running.get()) {
                        System.err.println(name + ": " + exception.getMessage());
                    }
                }
            }
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
                    double result = switch (fields[1]) {
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
                    output.println("RESULT|" + result);
                } catch (IllegalArgumentException | ArithmeticException exception) {
                    output.println("ERROR|" + exception.getMessage());
                }
            } catch (IOException exception) {
                System.err.println(name + ": falha na conexao: " + exception.getMessage());
            }
        }

        @Override
        public void close() throws IOException {
            running.set(false);
            socket.close();
        }
    }

    private record Endpoint(String host, int port) {
    }

    private static final class Client {
        private final String name;
        private final int directoryPort;

        private Client(String name, int directoryPort) {
            this.name = name;
            this.directoryPort = directoryPort;
        }

        private void calculate(String operation, double left, double right) throws IOException {
            String discovery = request(directoryPort, "GET|" + operation);
            String[] endpoint = discovery.split("\\|", -1);
            if (endpoint.length != 3 || !endpoint[0].equals("SERVER")) {
                throw new IOException("Descoberta de " + operation + " falhou: " + discovery);
            }
            String result = request(Integer.parseInt(endpoint[2]),
                    "CALC|" + operation + "|" + left + "|" + right);
            System.out.println(name + " descobriu " + endpoint[1] + ":" + endpoint[2]
                    + " para " + operation + " e recebeu " + result);
        }
    }
}
