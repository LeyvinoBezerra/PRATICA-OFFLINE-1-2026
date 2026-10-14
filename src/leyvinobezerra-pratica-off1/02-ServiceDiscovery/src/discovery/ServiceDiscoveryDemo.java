package discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class ServiceDiscoveryDemo {
    private ServiceDiscoveryDemo() {
    }

    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        // O demo usa portas efemeras para permitir execucoes paralelas sem conflito;
        // as classes de servidor tambem podem ser lancadas individualmente.
        try (DirectoryServer directory = new DirectoryServer(0, loopback)) {
            Thread directoryThread = start(directory, "diretorio");
            try (CalculatorServer serverA = new CalculatorServer("Calculadora-A", 0, loopback,
                    loopback.getHostAddress(),
                    new InetSocketAddress(loopback, directory.getPort()));
                 CalculatorServer serverB = new CalculatorServer("Calculadora-B", 0, loopback,
                         loopback.getHostAddress(),
                         new InetSocketAddress(loopback, directory.getPort()));
                 CalculatorServer serverC = new CalculatorServer("Calculadora-C", 0, loopback,
                         loopback.getHostAddress(),
                         new InetSocketAddress(loopback, directory.getPort()))) {
                Thread serverAThread = start(serverA, "calculadora-a");
                Thread serverBThread = start(serverB, "calculadora-b");
                Thread serverCThread = start(serverC, "calculadora-c");
                awaitServices(new InetSocketAddress(loopback, directory.getPort()));

                ServiceDiscoveryClient[] clients = {
                        new ServiceDiscoveryClient(new InetSocketAddress(loopback, directory.getPort())),
                        new ServiceDiscoveryClient(new InetSocketAddress(loopback, directory.getPort())),
                        new ServiceDiscoveryClient(new InetSocketAddress(loopback, directory.getPort()))
                };
                calculate(clients[0], "Cliente 1", "soma", 8, 5);
                calculate(clients[1], "Cliente 2", "soma", 10, 3);
                calculate(clients[2], "Cliente 3", "soma", 12, 4);
                calculate(clients[1], "Cliente 2", "multiplicacao", 7, 6);
                calculate(clients[2], "Cliente 3", "divisao", 20, 4);

                serverA.close();
                serverB.close();
                serverC.close();
                serverAThread.join(1000);
                serverBThread.join(1000);
                serverCThread.join(1000);
            } finally {
                directory.close();
                directoryThread.join(1000);
            }
        }
    }

    private static void calculate(ServiceDiscoveryClient client, String name, String operation,
                                  double left, double right) throws Exception {
        System.out.println(name + " executou " + operation + " e recebeu "
                + client.calculate(operation, left, right));
    }

    private static void awaitServices(InetSocketAddress directory) throws Exception {
        // A prontidao e confirmada pelo proprio protocolo em vez de depender de um
        // atraso fixo, que poderia falhar em maquinas mais lentas.
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean ready = true;
            for (String operation : new String[]{
                    "soma", "subtracao", "multiplicacao", "divisao"
            }) {
                try {
                    String response = ServiceDiscoveryClient.request(directory, "GET|" + operation);
                    if (!response.startsWith("SERVER|")) {
                        ready = false;
                        break;
                    }
                } catch (IOException exception) {
                    ready = false;
                    break;
                }
            }
            if (ready) {
                return;
            }
            Thread.sleep(25);
        }
        throw new IOException("Servidores de calculadora nao se registraram no diretorio");
    }

    private static Thread start(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
