package util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    // Um formato temporal comum facilita correlacionar eventos de processos distintos
    // durante a observacao dos fluxos distribuidos.
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {
    }

    public static synchronized void info(String origem, String mensagem) {
        // A sincronizacao evita que mensagens concorrentes aparecam intercaladas no terminal.
        System.out.println("[" + LocalTime.now().format(FORMATO) + "] [" + origem + "] " + mensagem);
    }

    public static synchronized void erro(String origem, String mensagem) {
        System.err.println("[" + LocalTime.now().format(FORMATO) + "] [" + origem + "] ERRO: " + mensagem);
    }
}
