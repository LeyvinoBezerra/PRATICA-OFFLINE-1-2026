package edge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import model.Requisicao;
import model.Resposta;
import util.Log;

public final class EdgeServer implements AutoCloseable {
    private static final int TIMEOUT_MILLIS = 5000;

    private final String nome;
    private final String hostOrigem;
    private final int portaOrigem;
    private final String hostAnunciado;
    private final ServerSocket servidor;
    private final Cache cache;
    // Pool limitado evita criar threads sem limite sob carga e mantem concorrentes
    // requisicoes de clientes isoladas do thread que aceita conexoes.
    private final ExecutorService atendentes = Executors.newFixedThreadPool(16);
    // A geracao por arquivo impede que uma resposta antiga da origem seja reinserida
    // no cache se uma invalidacao chegar enquanto o MISS ainda esta em andamento.
    private final ConcurrentHashMap<String, AtomicLong> geracoesInvalidacao =
            new ConcurrentHashMap<String, AtomicLong>();
    private final Object controleCache = new Object();
    private volatile boolean executando;

    public EdgeServer(String hostAnunciado, int porta, String hostOrigem, int portaOrigem,
                      int capacidadeCache, long ttlMillis) throws IOException {
        validarPorta(porta, "porta do edge");
        validarPorta(portaOrigem, "porta da origem");
        if (hostAnunciado == null || hostAnunciado.trim().isEmpty()) {
            throw new IllegalArgumentException("O host anunciado não pode ser vazio");
        }
        if (hostOrigem == null || hostOrigem.trim().isEmpty()) {
            throw new IllegalArgumentException("O host da origem não pode ser vazio");
        }
        this.hostAnunciado = hostAnunciado;
        this.hostOrigem = hostOrigem;
        this.portaOrigem = portaOrigem;
        this.cache = new Cache(capacidadeCache, ttlMillis);
        this.servidor = new ServerSocket();
        this.servidor.setReuseAddress(true);
        this.servidor.bind(new InetSocketAddress(porta));
        this.nome = hostAnunciado + ":" + porta;
    }

    public void iniciar() throws IOException {
        registrarNaOrigem();
        executando = true;
        Thread threadAceitacao = new Thread(this::aceitarConexoes, "edge-" + nome);
        threadAceitacao.start();
        Log.info(nome, "Servidor iniciado na porta " + servidor.getLocalPort());
    }

    private void registrarNaOrigem() throws IOException {
        try (Socket socket = conectarOrigem()) {
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            Requisicao.registrar(hostAnunciado, servidor.getLocalPort()).enviar(saida);
            Resposta resposta = Resposta.receber(entrada);
            if (!resposta.isSucesso()) {
                throw new IOException("Origem recusou registro: " + resposta.getMensagem());
            }
        }
    }

    private void aceitarConexoes() {
        while (executando) {
            try {
                Socket cliente = servidor.accept();
                atendentes.execute(() -> atender(cliente));
            } catch (SocketException e) {
                if (executando) {
                    Log.erro(nome, "Falha ao aceitar conexão: " + e.getMessage());
                }
            } catch (IOException e) {
                if (executando) {
                    Log.erro(nome, "Falha ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }

    private void atender(Socket socket) {
        try (Socket conexao = socket) {
            conexao.setSoTimeout(TIMEOUT_MILLIS);
            DataInputStream entrada = new DataInputStream(conexao.getInputStream());
            DataOutputStream saida = new DataOutputStream(conexao.getOutputStream());
            Requisicao requisicao = Requisicao.receber(entrada);
            Resposta resposta;
            if (Requisicao.BUSCAR.equals(requisicao.getOperacao())) {
                resposta = buscar(requisicao.getArquivo());
            } else if (Requisicao.INVALIDAR.equals(requisicao.getOperacao())) {
                resposta = invalidar(requisicao.getArquivo());
            } else {
                resposta = Resposta.erro("Operação não suportada pelo edge");
            }
            resposta.enviar(saida);
        } catch (IOException | IllegalArgumentException e) {
            Log.erro(nome, "Requisição inválida: " + e.getMessage());
        }
    }

    private Resposta buscar(String arquivo) throws IOException {
        validarNomeArquivo(arquivo);
        synchronized (controleCache) {
            Optional<String> valor = cache.buscar(arquivo);
            if (valor.isPresent()) {
                Log.info(nome, "HIT " + arquivo);
                return Resposta.sucesso("HIT", valor.get());
            }
        }

        Log.info(nome, "MISS " + arquivo);
        AtomicLong geracao = geracoesInvalidacao.computeIfAbsent(arquivo, chave -> new AtomicLong());
        // Busca na origem ocorre fora do lock para nao bloquear outros hits; a geracao
        // e conferida ao voltar para preservar invalidades concorrentes.
        long geracaoAntesDaBusca = geracao.get();
        Resposta respostaOrigem;
        try (Socket socket = conectarOrigem()) {
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            Requisicao.buscar(arquivo).enviar(saida);
            respostaOrigem = Resposta.receber(entrada);
        }
        if (!respostaOrigem.isSucesso()) {
            return respostaOrigem;
        }
        synchronized (controleCache) {
            if (geracao.get() == geracaoAntesDaBusca) {
                cache.armazenar(arquivo, respostaOrigem.getConteudo());
            }
        }
        return Resposta.sucesso("MISS", respostaOrigem.getConteudo());
    }

    private Resposta invalidar(String arquivo) {
        validarNomeArquivo(arquivo);
        AtomicLong geracao = geracoesInvalidacao.computeIfAbsent(arquivo, chave -> new AtomicLong());
        boolean removido;
        synchronized (controleCache) {
            // Aumentar a geracao mesmo quando ainda nao ha entrada remove a janela de
            // corrida entre uma leitura antiga da origem e a insercao no cache.
            geracao.incrementAndGet();
            removido = cache.invalidar(arquivo);
        }
        Log.info(nome, "INVALIDAÇÃO " + arquivo
                + (removido ? " (removido)" : " (não estava em cache)"));
        return Resposta.sucesso("Invalidação processada", "");
    }

    private Socket conectarOrigem() throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(hostOrigem, portaOrigem), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static void validarNomeArquivo(String arquivo) {
        if (arquivo == null || !arquivo.matches("file-[0-9]{4}\\.txt")) {
            throw new IllegalArgumentException("Nome inválido; esperado file-0001.txt até file-1000.txt");
        }
    }

    private static void validarPorta(int porta, String nome) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("Valor inválido para " + nome + ": " + porta);
        }
    }

    @Override
    public void close() {
        executando = false;
        try {
            servidor.close();
        } catch (IOException ignored) {
        }
        atendentes.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 6) {
            throw new IllegalArgumentException("Uso: EdgeServer <porta-edge> <host-origem> "
                    + "<porta-origem> <host-anunciado> [capacidade-cache] [ttl-segundos]");
        }
        int porta = parsearInteiro(args[0], "porta-edge");
        int portaOrigem = parsearInteiro(args[2], "porta-origem");
        int capacidade = args.length >= 5 ? parsearInteiro(args[4], "capacidade-cache") : 100;
        long ttlSegundos = args.length >= 6 ? parsearLongo(args[5], "ttl-segundos") : 10L;
        if (ttlSegundos <= 0 || ttlSegundos > Long.MAX_VALUE / 1000L) {
            throw new IllegalArgumentException("TTL deve ser positivo e expresso em segundos");
        }

        EdgeServer edge = new EdgeServer(args[3], porta, args[1], portaOrigem,
                capacidade, ttlSegundos * 1000L);
        Runtime.getRuntime().addShutdownHook(new Thread(edge::close, "edge-shutdown"));
        edge.iniciar();
        new CountDownLatch(1).await();
    }

    private static int parsearInteiro(String valor, String nome) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(nome + " deve ser um inteiro: " + valor, exception);
        }
    }

    private static long parsearLongo(String valor, String nome) {
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(nome + " deve ser um inteiro: " + valor, exception);
        }
    }
}
