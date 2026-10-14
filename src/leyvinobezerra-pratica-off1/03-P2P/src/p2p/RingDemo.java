package p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RingDemo {
    private static final int NODE_COUNT = 6;
    // A expressao regular valida a forma do identificador antes de converter o numero,
    // evitando aceitar nomes arbitrarios como chaves de busca.
    private static final Pattern FILE_NAME = Pattern.compile("arquivo([0-9]+)\\.ext");

    private RingDemo() {
    }

    public static void main(String[] args) throws Exception {
        List<RingNode> nodes = new ArrayList<>();
        try {
            for (int id = 0; id < NODE_COUNT; id++) {
                nodes.add(new RingNode(id));
            }
            for (RingNode node : nodes) {
                Thread thread = new Thread(node, "P" + node.id);
                thread.setDaemon(true);
                thread.start();
            }
            Thread.sleep(100);

            search(0, "arquivo35.ext");
            search(1, "arquivo99.ext");
            search(4, "arquivoABC.ext");
        } finally {
            for (RingNode node : nodes) {
                node.close();
            }
        }
    }

    private static void search(int origin, String file) {
        Matcher matcher = FILE_NAME.matcher(file);
        if (!matcher.matches()) {
            System.out.println("Erro na entrada de dados. Tente outra vez!");
            return;
        }
        int fileId;
        try {
            fileId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            System.out.println("Erro na entrada de dados. Tente outra vez!");
            return;
        }
        if (fileId < 1 || fileId > 60) {
            System.out.println("Erro na entrada de dados. Tente outra vez!");
            return;
        }
        try {
            String result = request(ports[origin], "SEARCH|" + file + "|0");
            System.out.println("Origem P" + origin + ", busca " + file + ": " + result);
        } catch (IOException exception) {
            System.err.println("Falha na busca: " + exception.getMessage());
        }
    }

    private static final int[] ports = new int[NODE_COUNT];

    private static String request(int port, String message) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             BufferedReader input = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true,
                     StandardCharsets.UTF_8)) {
            socket.setSoTimeout(5000);
            output.println(message);
            String response = input.readLine();
            if (response == null) {
                throw new IOException("anel encerrou a conexao sem resposta");
            }
            return response;
        }
    }

    private static final class RingNode implements Runnable {
        private final int id;
        // Cada peer possui uma faixa contigua e encaminha ao sucessor; predecessor e
        // sucessor calculados modularmente fecham o anel, inclusive nos extremos.
        private final int predecessor;
        private final int successor;
        private final int firstFile;
        private final int lastFile;
        private final ServerSocket socket;
        private final AtomicBoolean running = new AtomicBoolean(true);

        private RingNode(int id) throws IOException {
            this.id = id;
            predecessor = Math.floorMod(id - 1, NODE_COUNT);
            successor = (id + 1) % NODE_COUNT;
            firstFile = id * 10 + 1;
            lastFile = firstFile + 9;
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            ports[id] = socket.getLocalPort();
        }

        @Override
        public void run() {
            while (running.get()) {
                try {
                    Socket client = socket.accept();
                    Thread handler = new Thread(() -> handle(client), "P" + id + "-message");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException exception) {
                    if (running.get()) {
                        System.err.println("P" + id + ": " + exception.getMessage());
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
                    output.println("ERROR|comando invalido");
                    return;
                }
                String[] fields = line.split("\\|", -1);
                if (fields.length != 3 || !fields[0].equals("SEARCH")) {
                    output.println("ERROR|comando invalido");
                    return;
                }
                Matcher matcher = FILE_NAME.matcher(fields[1]);
                if (!matcher.matches()) {
                    output.println("ERROR|entrada invalida");
                    return;
                }
                int fileId = Integer.parseInt(matcher.group(1));
                if (fileId < 1 || fileId > 60) {
                    output.println("ERROR|arquivo inexistente");
                    return;
                }
                int hops = Integer.parseInt(fields[2]);
                if (hops < 0 || hops >= NODE_COUNT) {
                    output.println("ERROR|limite de saltos invalido");
                    return;
                }
                process(fields[1], fileId, hops, output);
            } catch (IOException | NumberFormatException exception) {
                System.err.println("P" + id + ": mensagem invalida: " + exception.getMessage());
            }
        }

        private void process(String file, int fileId, int hops, PrintWriter response) {
            System.out.println("P" + id + " recebeu busca por " + file);
            if (fileId >= firstFile && fileId <= lastFile) {
                System.out.println("P" + id + " encontrou " + file + " e enviou resposta");
                response.println("FOUND|P" + id);
                return;
            }
            if (hops >= NODE_COUNT - 1) {
                // O limite garante que uma busca sem correspondencia percorra no maximo
                // os outros peers uma vez, evitando ciclos infinitos.
                response.println("NOT_FOUND|" + file);
                return;
            }
            System.out.println("P" + id + " encaminhou " + file + " para P" + successor
                    + " (antecessor P" + predecessor + ")");
            try {
                String result = request(ports[successor], "SEARCH|" + file + "|" + (hops + 1));
                System.out.println("P" + id + " recebeu resposta de P" + successor + ": " + result);
                response.println(result);
            } catch (IOException exception) {
                response.println("ERROR|falha ao encaminhar para P" + successor);
                System.err.println("P" + id + ": falha de encaminhamento: " + exception.getMessage());
            }
        }

        private void close() {
            running.set(false);
            try {
                socket.close();
            } catch (IOException exception) {
                System.err.println("P" + id + ": falha ao encerrar: " + exception.getMessage());
            }
        }
    }
}
