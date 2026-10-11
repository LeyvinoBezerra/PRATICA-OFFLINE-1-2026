package cdn;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import cliente.Cliente;
import edge.EdgeServer;
import model.Requisicao;
import model.Resposta;
import origin.OriginServer;
import router.Router;

public final class Main {
    private static final int PORTA_ORIGEM_PADRAO = 5000;
    private static final int PORTA_ROTEADOR_PADRAO = 7000;

    @FunctionalInterface
    private interface InicioServidor {
        void iniciar() throws Exception;
    }

    private Main() {
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                uso();
                System.exit(2);
            }
            switch (args[0].toLowerCase()) {
                case "origin":
                    iniciarOrigem(args);
                    break;
                case "edge":
                    iniciarEdge(args);
                    break;
                case "router":
                    iniciarRoteador(args);
                    break;
                case "client":
                    executarCliente(args);
                    break;
                case "update":
                    atualizarArquivo(args);
                    break;
                default:
                    uso();
                    System.exit(2);
            }
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void iniciarOrigem(String[] args) throws Exception {
        exigirQuantidade(args, 1, 2, "origin [porta]");
        int porta = args.length == 2 ? inteiro(args[1], "porta") : PORTA_ORIGEM_PADRAO;
        OriginServer origem = new OriginServer(porta);
        executarServidor(origem::iniciar, origem);
    }

    private static void iniciarEdge(String[] args) throws Exception {
        exigirQuantidade(args, 5, 7,
                "edge <porta-edge> <host-origem> <porta-origem> <host-anunciado> [capacidade-cache] [ttl-segundos]");
        int portaEdge = inteiro(args[1], "porta-edge");
        int portaOrigem = inteiro(args[3], "porta-origem");
        int capacidade = args.length >= 6 ? inteiro(args[5], "capacidade-cache") : 100;
        long ttlSegundos = args.length >= 7 ? longo(args[6], "ttl-segundos") : 10L;
        if (ttlSegundos <= 0 || ttlSegundos > Long.MAX_VALUE / 1000L) {
            throw new IllegalArgumentException("TTL deve ser positivo e expresso em segundos");
        }
        EdgeServer edge = new EdgeServer(args[4], portaEdge, args[2], portaOrigem,
                capacidade, ttlSegundos * 1000L);
        executarServidor(edge::iniciar, edge);
    }

    private static void iniciarRoteador(String[] args) throws Exception {
        exigirQuantidade(args, 3, Integer.MAX_VALUE, "router <porta-udp> <localizacao=host:porta>...");
        int porta = inteiro(args[1], "porta-udp");
        Map<String, InetSocketAddress> rotas = new LinkedHashMap<String, InetSocketAddress>();
        for (int i = 2; i < args.length; i++) {
            int separador = args[i].indexOf('=');
            if (separador <= 0 || separador == args[i].length() - 1) {
                throw new IllegalArgumentException("Rota inválida, use localizacao=host:porta: " + args[i]);
            }
            String localizacao = args[i].substring(0, separador);
            if (rotas.put(localizacao, Router.parsearEndereco(args[i].substring(separador + 1))) != null) {
                throw new IllegalArgumentException("Localização duplicada: " + localizacao);
            }
        }
        Router router = new Router(porta, rotas);
        executarServidor(router::iniciar, router);
    }

    private static void executarCliente(String[] args) throws Exception {
        exigirQuantidade(args, 5, 5, "client <host-router> <porta-router> <localizacao> <arquivo>");
        Resposta resposta = Cliente.baixar(args[1], inteiro(args[2], "porta-router"), args[3], args[4]);
        if (!resposta.isSucesso()) {
            throw new IllegalStateException(resposta.getMensagem());
        }
        System.out.println("Resultado: " + resposta.getMensagem());
        System.out.println(resposta.getConteudo());
    }

    private static void atualizarArquivo(String[] args) throws Exception {
        exigirQuantidade(args, 5, 5, "update <host-origem> <porta-origem> <arquivo> <novo-conteudo>");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(args[1], inteiro(args[2], "porta-origem")), 5000);
            socket.setSoTimeout(5000);
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            Requisicao.atualizar(args[3], args[4]).enviar(saida);
            Resposta resposta = Resposta.receber(entrada);
            if (!resposta.isSucesso()) {
                throw new IllegalStateException(resposta.getMensagem());
            }
            System.out.println(resposta.getMensagem());
        }
    }

    private static void executarServidor(InicioServidor inicio, AutoCloseable servidor) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                servidor.close();
            } catch (Exception ignored) {
            }
        }, "cdn-shutdown"));
        inicio.iniciar();
        new CountDownLatch(1).await();
    }

    private static void exigirQuantidade(String[] args, int minimo, int maximo, String sintaxe) {
        if (args.length < minimo || args.length > maximo) {
            throw new IllegalArgumentException("Uso: " + sintaxe);
        }
    }

    private static int inteiro(String valor, String nome) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nome + " deve ser um inteiro: " + valor, e);
        }
    }

    private static long longo(String valor, String nome) {
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nome + " deve ser um inteiro: " + valor, e);
        }
    }

    private static void uso() {
        System.err.println("Comandos:");
        System.err.println("  origin [porta]");
        System.err.println("  edge <porta-edge> <host-origem> <porta-origem> <host-anunciado> [capacidade-cache] [ttl-segundos]");
        System.err.println("  router <porta-udp> <localizacao=host:porta>...");
        System.err.println("  client <host-router> <porta-router> <localizacao> <arquivo>");
        System.err.println("  update <host-origem> <porta-origem> <arquivo> <novo-conteudo>");
    }
}
