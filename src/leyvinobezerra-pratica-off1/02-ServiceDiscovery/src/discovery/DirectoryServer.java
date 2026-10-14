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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class DirectoryServer implements Runnable, AutoCloseable {
    private final ServerSocket socket;
    // O mapa concorrente protege o catalogo entre handlers; listas copy-on-write
    // favorecem consultas frequentes, que leem muito mais do que atualizacoes.
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Endpoint>> services =
            new ConcurrentHashMap<>();
    // Um contador atomico por operacao permite round robin sem bloquear todas as consultas.
    private final ConcurrentHashMap<String, AtomicInteger> nextServer = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DirectoryServer(int port, InetAddress bindAddress) throws IOException {
        socket = new ServerSocket();
        socket.bind(new InetSocketAddress(bindAddress, port));
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    public void run() {
        System.out.println("Diretorio ouvindo em " + socket.getLocalSocketAddress());
        while (running.get()) {
            try {
                Socket client = socket.accept();
                startHandler(client);
            } catch (IOException exception) {
                if (running.get()) {
                    System.err.println("Diretorio: " + exception.getMessage());
                }
            }
        }
    }

    private void startHandler(Socket client) {
        // Cada conexao e atendida fora do acceptor para uma requisicao lenta nao impedir
        // o registro ou a descoberta de outros clientes.
        Thread thread = new Thread(() -> handle(client), "directory-client");
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
                output.println("ERROR|requisicao vazia");
                return;
            }
            String[] fields = line.split("\\|", -1);
            if (fields.length == 4 && fields[0].equals("REGISTER")) {
                register(fields, output);
            } else if (fields.length == 2 && fields[0].equals("GET")) {
                lookup(fields[1], output);
            } else {
                output.println("ERROR|requisicao invalida");
            }
        } catch (IOException exception) {
            System.err.println("Diretorio: requisicao invalida: " + exception.getMessage());
        }
    }

    private void register(String[] fields, PrintWriter output) {
        int port;
        try {
            port = Integer.parseInt(fields[3]);
        } catch (NumberFormatException exception) {
            output.println("ERROR|porta invalida");
            return;
        }
        if (fields[1].isBlank() || fields[2].isBlank() || port < 1 || port > 65535) {
            output.println("ERROR|registro invalido");
            return;
        }

        Endpoint endpoint = new Endpoint(fields[2], port);
        services.computeIfAbsent(fields[1], ignored -> new CopyOnWriteArrayList<>())
                .addIfAbsent(endpoint);
        output.println("OK|registrado");
    }

    private void lookup(String service, PrintWriter output) {
        List<Endpoint> endpoints = services.get(service);
        if (endpoints == null || endpoints.isEmpty()) {
            output.println("ERROR|servico indisponivel");
            return;
        }
        // floorMod mantem o indice valido mesmo quando o contador atomico transborda.
        int index = Math.floorMod(nextServer.computeIfAbsent(service, ignored -> new AtomicInteger())
                .getAndIncrement(), endpoints.size());
        Endpoint endpoint = endpoints.get(index);
        output.println("SERVER|" + endpoint.host() + "|" + endpoint.port());
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        socket.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException("Uso: DirectoryServer [porta] [endereco-bind]");
        }
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 5000;
        InetAddress bindAddress = args.length == 2
                ? InetAddress.getByName(args[1])
                : InetAddress.getLoopbackAddress();
        try (DirectoryServer directory = new DirectoryServer(port, bindAddress)) {
            directory.run();
        }
    }

    private record Endpoint(String host, int port) {
    }
}
