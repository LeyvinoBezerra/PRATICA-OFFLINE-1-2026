package minidns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public final class MiniDnsClient implements AutoCloseable {
    private final String name;
    private final DatagramSocket socket;
    private final InetSocketAddress server;

    public MiniDnsClient(String name, InetSocketAddress server) throws IOException {
        this.name = name;
        this.server = server;
        socket = new DatagramSocket();
        // UDP nao confirma entrega: timeout impede que uma consulta perdida bloqueie
        // indefinidamente o cliente e permite a aplicacao decidir o que fazer.
        socket.setSoTimeout(3000);
    }

    public String getName() {
        return name;
    }

    public String lookup(String hostname) throws IOException {
        // O mesmo socket e reutilizado para que o servidor possa associar o endpoint
        // consultante a futuras notificacoes UPDATE.
        send("GET|" + hostname);
        String[] response = receive().split("\\|", -1);
        if (response.length != 2 || !response[0].equals("VALUE")) {
            throw new IOException("Falha na consulta de " + hostname + ": "
                    + String.join("|", response));
        }
        return response[1];
    }

    public String updateAddress(String hostname, String address) throws IOException {
        send("SET|" + hostname + "|" + address);
        String response = receive();
        if (!response.equals("OK|" + hostname)) {
            throw new IOException("Falha ao atualizar " + hostname + ": " + response);
        }
        return response;
    }

    public String receiveUpdate() throws IOException {
        String[] response = receive().split("\\|", -1);
        if (response.length != 3 || !response[0].equals("UPDATE")) {
            throw new IOException("Notificacao invalida recebida por " + name);
        }
        return name + " recebeu atualizacao: " + response[1] + " -> " + response[2];
    }

    private void send(String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(bytes, bytes.length, server));
    }

    private String receive() throws IOException {
        byte[] bytes = new byte[1024];
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);
        return new String(packet.getData(), packet.getOffset(), packet.getLength(),
                StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        socket.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Uso: MiniDnsClient [visitante|registrador] "
                    + "nome [endereco] [servidor] [porta]");
        }
        boolean visitorMode = args[0].equalsIgnoreCase("visitante");
        boolean registrarMode = args[0].equalsIgnoreCase("registrador");
        boolean explicitMode = visitorMode || registrarMode;
        int hostnameIndex = explicitMode ? 1 : 0;
        int addressIndex = registrarMode ? 2 : -1;
        int serverIndex = explicitMode ? (registrarMode ? 3 : 2) : 1;
        int maxArgs = explicitMode ? (registrarMode ? 5 : 4) : 3;
        if (args.length <= hostnameIndex || args.length > maxArgs
                || (registrarMode && args.length <= addressIndex)) {
            throw new IllegalArgumentException("Uso: MiniDnsClient [visitante|registrador] "
                    + "nome [endereco] [servidor] [porta]");
        }

        String hostname = args[hostnameIndex];
        String address = registrarMode ? args[addressIndex] : null;
        InetAddress serverAddress = args.length > serverIndex
                ? InetAddress.getByName(args[serverIndex])
                : InetAddress.getLoopbackAddress();
        int portIndex = serverIndex + 1;
        int port = args.length > portIndex ? Integer.parseInt(args[portIndex]) : 5354;
        InetSocketAddress endpoint = new InetSocketAddress(serverAddress, port);

        try (MiniDnsClient client = new MiniDnsClient("Cliente", endpoint)) {
            if (registrarMode) {
                System.out.println(client.updateAddress(hostname, address));
                return;
            }
            System.out.println("Consulta de " + hostname + ": " + client.lookup(hostname));
            System.out.println("Aguardando atualizacoes do MiniDNS (Ctrl+C para sair).");
            while (true) {
                try {
                    System.out.println(client.receiveUpdate());
                } catch (SocketTimeoutException ignored) {
                    // Timeout esperado enquanto nao ha atualizacoes.
                }
            }
        }
    }
}
