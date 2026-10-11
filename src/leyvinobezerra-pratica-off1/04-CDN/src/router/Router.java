package router;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import util.Log;

public final class Router implements AutoCloseable {
    private final DatagramSocket socket;
    private final Map<String, InetSocketAddress> rotas;
    private volatile boolean executando;

    public Router(int porta, Map<String, InetSocketAddress> rotas) throws SocketException {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("Porta deve estar entre 1 e 65535");
        }
        if (rotas == null || rotas.isEmpty()) {
            throw new IllegalArgumentException("Configure ao menos uma rota de localização");
        }
        this.rotas = Collections.unmodifiableMap(new LinkedHashMap<String, InetSocketAddress>(rotas));
        this.socket = new DatagramSocket(porta);
    }

    public void iniciar() {
        executando = true;
        Thread thread = new Thread(this::atender, "router-udp");
        thread.start();
        Log.info("ROUTER", "Servidor UDP iniciado na porta " + socket.getLocalPort()
                + " com " + rotas.size() + " rotas");
    }

    private void atender() {
        byte[] buffer = new byte[1024];
        while (executando) {
            DatagramPacket pedido = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(pedido);
                String localizacao = new String(pedido.getData(), pedido.getOffset(),
                        pedido.getLength(), StandardCharsets.UTF_8).trim();
                InetSocketAddress edge = rotas.get(localizacao);
                String resposta = edge == null ? "ERROR Localização desconhecida: " + localizacao
                        : edge.getHostString() + ":" + edge.getPort();
                byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(bytes, bytes.length, pedido.getAddress(), pedido.getPort()));
            } catch (SocketException e) {
                if (executando) {
                    Log.erro("ROUTER", "Falha no socket UDP: " + e.getMessage());
                }
            } catch (IOException e) {
                if (executando) {
                    Log.erro("ROUTER", "Falha ao responder: " + e.getMessage());
                }
            }
        }
    }

    public static InetSocketAddress parsearEndereco(String valor) {
        int separador = valor.lastIndexOf(':');
        if (separador <= 0 || separador == valor.length() - 1) {
            throw new IllegalArgumentException("Endereço inválido, use host:porta: " + valor);
        }
        try {
            String host = valor.substring(0, separador);
            int porta = Integer.parseInt(valor.substring(separador + 1));
            if (host.trim().isEmpty() || porta < 1 || porta > 65535) {
                throw new IllegalArgumentException("Endereço inválido, use host:porta: " + valor);
            }
            return new InetSocketAddress(InetAddress.getByName(host), porta);
        } catch (IOException | NumberFormatException e) {
            throw new IllegalArgumentException("Endereço inválido: " + valor, e);
        }
    }

    @Override
    public void close() {
        executando = false;
        socket.close();
    }
}
