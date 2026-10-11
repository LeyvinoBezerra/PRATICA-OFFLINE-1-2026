package cliente;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import model.Requisicao;
import model.Resposta;

public final class Cliente {
    private static final int TIMEOUT_MILLIS = 5000;

    private Cliente() {
    }

    public static Resposta baixar(String hostRoteador, int portaRoteador,
                                  String localizacao, String arquivo) throws IOException {
        InetSocketAddress edge = consultarRoteador(hostRoteador, portaRoteador, localizacao);
        try (Socket socket = new Socket()) {
            socket.connect(edge, TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            Requisicao.buscar(arquivo).enviar(saida);
            return Resposta.receber(entrada);
        }
    }

    private static InetSocketAddress consultarRoteador(String host, int porta, String localizacao)
            throws IOException {
        byte[] pedido = localizacao.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            InetAddress enderecoRoteador = InetAddress.getByName(host);
            socket.send(new DatagramPacket(pedido, pedido.length, enderecoRoteador, porta));
            byte[] buffer = new byte[1024];
            DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(resposta);
            } catch (SocketTimeoutException e) {
                throw new IOException("Tempo esgotado aguardando resposta do roteador", e);
            }
            String texto = new String(resposta.getData(), resposta.getOffset(),
                    resposta.getLength(), StandardCharsets.UTF_8);
            if (texto.startsWith("ERROR ")) {
                throw new IOException("Roteador: " + texto.substring(6));
            }
            int separador = texto.lastIndexOf(':');
            if (separador <= 0 || separador == texto.length() - 1) {
                throw new IOException("Resposta inválida do roteador: " + texto);
            }
            try {
                int portaEdge = Integer.parseInt(texto.substring(separador + 1));
                return new InetSocketAddress(texto.substring(0, separador), portaEdge);
            } catch (IllegalArgumentException e) {
                throw new IOException("Endereço inválido recebido do roteador: " + texto, e);
            }
        }
    }
}
