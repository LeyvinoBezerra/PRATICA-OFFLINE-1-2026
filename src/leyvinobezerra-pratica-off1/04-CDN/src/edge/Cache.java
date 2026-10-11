package edge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Cache {
    private static final class Entrada {
        private final String conteudo;
        private final long expiraEm;

        private Entrada(String conteudo, long expiraEm) {
            this.conteudo = conteudo;
            this.expiraEm = expiraEm;
        }
    }

    private final int capacidade;
    private final long ttlMillis;
    private final LinkedHashMap<String, Entrada> entradas =
            new LinkedHashMap<String, Entrada>(16, 0.75f, true);

    public Cache(int capacidade, long ttlMillis) {
        if (capacidade <= 0) {
            throw new IllegalArgumentException("A capacidade do cache deve ser positiva");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("O TTL deve ser positivo");
        }
        this.capacidade = capacidade;
        this.ttlMillis = ttlMillis;
    }

    public synchronized Optional<String> buscar(String arquivo) {
        Entrada entrada = entradas.get(arquivo);
        if (entrada == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() >= entrada.expiraEm) {
            entradas.remove(arquivo);
            return Optional.empty();
        }
        return Optional.of(entrada.conteudo);
    }

    public synchronized void armazenar(String arquivo, String conteudo) {
        entradas.put(arquivo, new Entrada(conteudo, System.currentTimeMillis() + ttlMillis));
        while (entradas.size() > capacidade) {
            String maisAntigo = entradas.keySet().iterator().next();
            entradas.remove(maisAntigo);
        }
    }

    public synchronized boolean invalidar(String arquivo) {
        return entradas.remove(arquivo) != null;
    }

    public synchronized int tamanho() {
        removerExpirados();
        return entradas.size();
    }

    private void removerExpirados() {
        long agora = System.currentTimeMillis();
        entradas.entrySet().removeIf(entry -> agora >= entry.getValue().expiraEm);
    }
}
