package minidns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MiniDnsDemo {
    private static final int PORT = 5354;
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

    private MiniDnsDemo() {
    }

    public static void main(String[] args) throws Exception {
        try (MiniDnsServer server = new MiniDnsServer(PORT);
             MiniDnsClient cliente1 = new MiniDnsClient("Cliente 1");
             MiniDnsClient cliente2 = new MiniDnsClient("Cliente 2");
             MiniDnsClient cliente3 = new MiniDnsClient("Cliente 3");
             DatagramSocket registrador = new DatagramSocket()) {
            Thread serverThread = new Thread(server, "minidns-server");
            serverThread.setDaemon(true);
            serverThread.start();
            Thread.sleep(100);

            MiniDnsClient[] clients = {cliente1, cliente2, cliente3};
            for (MiniDnsClient client : clients) {
                for (String name : new String[]{"servidor1", "servidor4", "servidor9"}) {
                    System.out.println(client.name + " consulta " + name + ": "
                            + client.lookup(name));
                }
            }

            Map<String, String> updates = Map.of(
                    "servidor1", "10.0.1.11",
                    "servidor4", "10.0.4.44",
                    "servidor9", "10.0.9.99");
            for (Map.Entry<String, String> update : updates.entrySet()) {
                send(registrador, "SET|" + update.getKey() + "|" + update.getValue(),
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));
                System.out.println("Registrador: " + receive(registrador));
            }

            for (MiniDnsClient client : clients) {
                for (int i = 0; i < updates.size(); i++) {
                    System.out.println(client.receiveUpdate());
                }
            }
        }
    }

    private static void send(DatagramSocket socket, String message, InetSocketAddress address)
            throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(bytes, bytes.length, address));
    }

    private static String receive(DatagramSocket socket) throws IOException {
        byte[] bytes = new byte[1024];
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);
        return new String(packet.getData(), packet.getOffset(), packet.getLength(),
                StandardCharsets.UTF_8);
    }

    private static final class MiniDnsServer implements Runnable, AutoCloseable {
        private final DatagramSocket socket;
        private final Map<String, String> entries = new ConcurrentHashMap<>(INITIAL_ENTRIES);
        private final Map<String, Set<InetSocketAddress>> subscribers = new ConcurrentHashMap<>();
        private volatile boolean running = true;

        private MiniDnsServer(int port) throws IOException {
            socket = new DatagramSocket(port, InetAddress.getLoopbackAddress());
        }

        @Override
        public void run() {
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
            String[] fields = new String(packet.getData(), packet.getOffset(),
                    packet.getLength(), StandardCharsets.UTF_8).split("\\|", -1);
            InetSocketAddress requester = new InetSocketAddress(packet.getAddress(), packet.getPort());
            if (fields.length == 2 && fields[0].equals("GET")) {
                String address = entries.get(fields[1]);
                if (address == null) {
                    send(socket, "ERROR|nome desconhecido", requester);
                } else {
                    subscribers.computeIfAbsent(fields[1], ignored -> ConcurrentHashMap.newKeySet())
                            .add(requester);
                    send(socket, "VALUE|" + address, requester);
                }
            } else if (fields.length == 3 && fields[0].equals("SET")
                    && validIpv4(fields[2]) && entries.containsKey(fields[1])) {
                entries.put(fields[1], fields[2]);
                send(socket, "OK|" + fields[1], requester);
                String notification = "UPDATE|" + fields[1] + "|" + fields[2];
                for (InetSocketAddress subscriber : subscribers.getOrDefault(fields[1], Set.of())) {
                    send(socket, notification, subscriber);
                }
            } else {
                send(socket, "ERROR|comando invalido", requester);
            }
        }

        private static boolean validIpv4(String value) {
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
    }

    private static final class MiniDnsClient implements AutoCloseable {
        private final String name;
        private final DatagramSocket socket;
        private final InetSocketAddress server =
                new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT);

        private MiniDnsClient(String name) throws IOException {
            this.name = name;
            socket = new DatagramSocket();
            socket.setSoTimeout(3000);
        }

        private String lookup(String hostname) throws IOException {
            send(socket, "GET|" + hostname, server);
            String[] response = receive(socket).split("\\|", -1);
            if (response.length != 2 || !response[0].equals("VALUE")) {
                throw new IOException("Falha na consulta de " + hostname + ": "
                        + String.join("|", response));
            }
            return response[1];
        }

        private String receiveUpdate() throws IOException {
            String[] response;
            try {
                response = receive(socket).split("\\|", -1);
            } catch (SocketTimeoutException exception) {
                throw new IOException(name + " nao recebeu atualizacao do MiniDNS", exception);
            }
            if (response.length != 3 || !response[0].equals("UPDATE")) {
                throw new IOException("Notificacao invalida recebida por " + name);
            }
            return name + " recebeu atualizacao: " + response[1] + " -> " + response[2];
        }

        @Override
        public void close() {
            socket.close();
        }
    }
}
