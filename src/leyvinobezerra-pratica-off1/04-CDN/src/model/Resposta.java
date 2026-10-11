package model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class Resposta {
    private final boolean sucesso;
    private final String mensagem;
    private final String conteudo;

    private Resposta(boolean sucesso, String mensagem, String conteudo) {
        this.sucesso = sucesso;
        this.mensagem = mensagem == null ? "" : mensagem;
        this.conteudo = conteudo == null ? "" : conteudo;
    }

    public static Resposta sucesso(String mensagem, String conteudo) {
        return new Resposta(true, mensagem, conteudo);
    }

    public static Resposta erro(String mensagem) {
        return new Resposta(false, mensagem, "");
    }

    public void enviar(DataOutputStream saida) throws IOException {
        saida.writeBoolean(sucesso);
        saida.writeUTF(mensagem);
        saida.writeUTF(conteudo);
        saida.flush();
    }

    public static Resposta receber(DataInputStream entrada) throws IOException {
        boolean sucesso = entrada.readBoolean();
        String mensagem = entrada.readUTF();
        String conteudo = entrada.readUTF();
        return new Resposta(sucesso, mensagem, conteudo);
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getConteudo() {
        return conteudo;
    }
}
