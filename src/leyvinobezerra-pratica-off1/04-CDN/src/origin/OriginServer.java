package origin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import model.Requisicao;
import model.Resposta;
import util.Log;

public final class OriginServer implements AutoCloseable {
    private static final int TIMEOUT_MILLIS = 5000;

    private final ServerSocket servidor;
    private final ExecutorService atendentes = Executors.newFixedThreadPool(16);
    private final Map<String, String> arquivos = new ConcurrentHashMap<String, String>();
    private final Map<String, InetSocketAddress> edges = new ConcurrentHashMap<String, InetSocketAddress>();
    private volatile boolean executando;

    public OriginServer(int porta) throws IOException {
        validarPorta(porta);
        servidor = new ServerSocket();
        servidor.setReuseAddress(true);
        servidor.bind(new InetSocketAddress(porta));
        for (int i = 1; i <= 1000; i++) {
            String nome = String.format("file-%04d.txt", i);
            arquivos.put(nome, "Conteúdo simulado do arquivo " + nome + " (versão inicial).");
        }
    }

    public void iniciar() {
        executando = true;
        Thread thread = new Thread(this::aceitarConexoes, "origin-accept");
        thread.start();
        Log.info("ORIGIN", "Servidor iniciado na porta " + servidor.getLocalPort()
                + " com " + arquivos.size() + " arquivos simulados");
    }

    private void aceitarConexoes() {
        while (executando) {
            try {
                Socket cliente = servidor.accept();
                atendentes.execute(() -> atender(cliente));
            } catch (SocketException e) {
                if (executando) {
                    Log.erro("ORIGIN", "Falha ao aceitar conexão: " + e.getMessage());
                }
            } catch (IOException e) {
                if (executando) {
                    Log.erro("ORIGIN", "Falha ao aceitar conexão: " + e.getMessage());
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
            switch (requisicao.getOperacao()) {
                case Requisicao.BUSCAR:
                    resposta = buscar(requisicao.getArquivo());
                    break;
                case Requisicao.REGISTRAR:
                    resposta = registrar(requisicao.getHost(), requisicao.getPorta());
                    break;
                case Requisicao.ATUALIZAR:
                    resposta = atualizar(requisicao.getArquivo(), requisicao.getConteudo());
                    break;
                default:
                    resposta = Resposta.erro("Operação não suportada pela origem");
            }
            resposta.enviar(saida);
        } catch (IOException | IllegalArgumentException e) {
            Log.erro("ORIGIN", "Requisição inválida: " + e.getMessage());
        }
    }

    private Resposta buscar(String arquivo) {
        if (!nomeValido(arquivo)) {
            return Resposta.erro("Nome inválido; esperado file-0001.txt até file-1000.txt");
        }
        String conteudo = arquivos.get(arquivo);
        return conteudo == null ? Resposta.erro("Arquivo não encontrado: " + arquivo)
                : Resposta.sucesso("OK", conteudo);
    }

    private Resposta registrar(String host, int porta) {
        if (host == null || host.trim().isEmpty() || porta < 1 || porta > 65535) {
            return Resposta.erro("Endereço de edge inválido");
        }
        String id = host + ":" + porta;
        edges.put(id, new InetSocketAddress(host, porta));
        Log.info("ORIGIN", "Edge registrado: " + id);
        return Resposta.sucesso("Edge registrado", "");
    }

    private Resposta atualizar(String arquivo, String conteudo) {
        if (!nomeValido(arquivo)) {
            return Resposta.erro("Nome inválido; esperado file-0001.txt até file-1000.txt");
        }
        if (conteudo == null) {
            return Resposta.erro("O conteúdo não pode ser nulo");
        }
        if (!arquivos.containsKey(arquivo)) {
            return Resposta.erro("Arquivo não encontrado: " + arquivo);
        }
        arquivos.put(arquivo, conteudo);
        int invalidacoes = invalidarEdges(arquivo);
        Log.info("ORIGIN", "Arquivo atualizado: " + arquivo + "; invalidações enviadas=" + invalidacoes);
        return Resposta.sucesso("Arquivo atualizado; invalidações enviadas=" + invalidacoes, "");
    }

    private int invalidarEdges(String arquivo) {
        int enviadas = 0;
        for (Map.Entry<String, InetSocketAddress> edge : edges.entrySet()) {
            try (Socket socket = new Socket()) {
                socket.connect(edge.getValue(), TIMEOUT_MILLIS);
                socket.setSoTimeout(TIMEOUT_MILLIS);
                DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                Requisicao.invalidar(arquivo).enviar(saida);
                Resposta resposta = Resposta.receber(entrada);
                if (resposta.isSucesso()) {
                    enviadas++;
                }
            } catch (IOException e) {
                Log.erro("ORIGIN", "Não foi possível invalidar " + edge.getKey() + ": " + e.getMessage());
            }
        }
        return enviadas;
    }

    private static boolean nomeValido(String arquivo) {
        return arquivo != null && arquivo.matches("file-[0-9]{4}\\.txt");
    }

    private static void validarPorta(int porta) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("Porta deve estar entre 1 e 65535");
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
}
