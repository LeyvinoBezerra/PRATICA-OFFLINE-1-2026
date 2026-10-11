package model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class Requisicao {
    public static final String BUSCAR = "FETCH";
    public static final String REGISTRAR = "REGISTER";
    public static final String INVALIDAR = "INVALIDATE";
    public static final String ATUALIZAR = "PUT";

    private final String operacao;
    private final String arquivo;
    private final String conteudo;
    private final String host;
    private final int porta;

    private Requisicao(String operacao, String arquivo, String conteudo, String host, int porta) {
        this.operacao = operacao;
        this.arquivo = arquivo;
        this.conteudo = conteudo;
        this.host = host;
        this.porta = porta;
    }

    public static Requisicao buscar(String arquivo) {
        return new Requisicao(BUSCAR, arquivo, null, null, 0);
    }

    public static Requisicao registrar(String host, int porta) {
        return new Requisicao(REGISTRAR, null, null, host, porta);
    }

    public static Requisicao invalidar(String arquivo) {
        return new Requisicao(INVALIDAR, arquivo, null, null, 0);
    }

    public static Requisicao atualizar(String arquivo, String conteudo) {
        return new Requisicao(ATUALIZAR, arquivo, conteudo, null, 0);
    }

    public void enviar(DataOutputStream saida) throws IOException {
        saida.writeUTF(operacao);
        switch (operacao) {
            case BUSCAR:
            case INVALIDAR:
                saida.writeUTF(arquivo);
                break;
            case REGISTRAR:
                saida.writeUTF(host);
                saida.writeInt(porta);
                break;
            case ATUALIZAR:
                saida.writeUTF(arquivo);
                saida.writeUTF(conteudo);
                break;
            default:
                throw new IOException("Operação desconhecida: " + operacao);
        }
        saida.flush();
    }

    public static Requisicao receber(DataInputStream entrada) throws IOException {
        String operacao = entrada.readUTF();
        switch (operacao) {
            case BUSCAR:
            case INVALIDAR:
                return new Requisicao(operacao, entrada.readUTF(), null, null, 0);
            case REGISTRAR:
                return new Requisicao(operacao, null, null, entrada.readUTF(), entrada.readInt());
            case ATUALIZAR:
                return new Requisicao(operacao, entrada.readUTF(), entrada.readUTF(), null, 0);
            default:
                throw new IOException("Operação desconhecida: " + operacao);
        }
    }

    public String getOperacao() {
        return operacao;
    }

    public String getArquivo() {
        return arquivo;
    }

    public String getConteudo() {
        return conteudo;
    }

    public String getHost() {
        return host;
    }

    public int getPorta() {
        return porta;
    }
}
