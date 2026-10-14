package minidns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MiniDnsServer implements Runnable, AutoCloseable {
    // A tabela inicial e imutavel serve como configuracao; cada servidor cria sua
    // propria copia concorrente para que SETs nao alterem a configuracao global.
    private static final Map<String, String> INITIAL_ENTRIES = Map.of(
            "servidor1", "192.168.0.10",
            "servidor2", "192.168.0.20",
            "servidor3", "192.168.0.30",
            "servidor4", "192.168.0.40",
            "servidor5", "192.168.0.50",
            "servidor6", "192.168.0.60",
            "servidor7", "192.168.0.70",
            "servidor8", "192.168.0.80",
            "servidor9", "192.168.0.90",
            "servidor10", "192.168.0.100");

    private final DatagramSocket socket;
    private final Map<String, String> entries = new ConcurrentHashMap<>(INITIAL_ENTRIES);
    // Um cliente pode consultar varios nomes; cada conjunto permite remover duplicatas
    // e receber notificacoes apenas dos nomes que aquele endpoint consultou.
    private final Map<String, Set<InetSocketAddress>> subscribers = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public MiniDnsServer(int port) throws SocketException {
        this(port, InetAddress.getLoopbackAddress());
    }

    public MiniDnsServer(int port, InetAddress bindAddress) throws SocketException {
        socket = new DatagramSocket(new InetSocketAddress(bindAddress, port));
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    @Override
    public void run() {
        System.out.println("MiniDNS ouvindo em " + socket.getLocalSocketAddress());
        while (running) {
            try {
                byte[] bytes = new byte[1024];
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                socket.receive(packet);
                handle(packet);
            } catch (IOException exception) {
                if (running) {
                    System.err.println("MiniDNS: falha ao receber datagrama: "
                            + exception.getMessage());
                }
            }
        }
    }

    private void handle(DatagramPacket packet) throws IOException {
        // O remetente do datagrama identifica o endpoint de retorno sem exigir uma
        // conexao persistente; GET e SET tambem distinguem os papeis neste protocolo didatico.
        String[] fields = new String(packet.getData(), packet.getOffset(),
                packet.getLength(), StandardCharsets.UTF_8).split("\\|", -1);
        InetSocketAddress requester = new InetSocketAddress(packet.getAddress(), packet.getPort());
        if (fields[0].equals("GET")) {
            handleVisitorRequest(fields, requester);
        } else if (fields[0].equals("SET")) {
            handleRegistrarRequest(fields, requester);
        } else {
            send("ERROR|comando invalido", requester);
        }
    }

    private void handleVisitorRequest(String[] fields, InetSocketAddress requester)
            throws IOException {
        if (fields.length != 2) {
            send("ERROR|comando invalido", requester);
            return;
        }
        String hostname = fields[1];
        String address = entries.get(hostname);
        if (address == null) {
            send("ERROR|nome desconhecido", requester);
            return;
        }

        subscribers.computeIfAbsent(hostname, ignored -> ConcurrentHashMap.newKeySet())
                .add(requester);
        System.out.println("Cliente visitante " + requester + " consultou " + hostname);
        send("VALUE|" + address, requester);
    }

    private void handleRegistrarRequest(String[] fields, InetSocketAddress requester)
            throws IOException {
        if (fields.length != 3) {
            send("ERROR|comando invalido", requester);
            return;
        }
        String hostname = fields[1];
        String address = fields[2];
        if (!entries.containsKey(hostname)) {
            send("ERROR|nome desconhecido", requester);
            return;
        }
        if (!validIpv4(address)) {
            send("ERROR|endereco IPv4 invalido", requester);
            return;
        }

        entries.put(hostname, address);
        System.out.println("Cliente registrador " + requester + " atualizou "
                + hostname + " para " + address);
        send("OK|" + hostname, requester);
        String notification = "UPDATE|" + hostname + "|" + address;
        for (InetSocketAddress subscriber : subscribers.getOrDefault(hostname, Set.of())) {
            send(notification, subscriber);
        }
    }

    private void send(String message, InetSocketAddress address) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(bytes, bytes.length, address));
    }

    private static boolean validIpv4(String value) {
        // Validar cada octeto evita aceitar valores que o parser DNS/IP poderia normalizar
        // silenciosamente, mantendo a representacao armazenada previsivel.
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                int number = Integer.parseInt(octet);
                if (number < 0 || number > 255 || !octet.equals(Integer.toString(number))) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        running = false;
        socket.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException("Uso: MiniDnsServer [porta] [endereco-bind]");
        }
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 5354;
        InetAddress bindAddress = args.length == 2
                ? InetAddress.getByName(args[1])
                : InetAddress.getLoopbackAddress();
        try (MiniDnsServer server = new MiniDnsServer(port, bindAddress)) {
            server.run();
        }
    }
}
