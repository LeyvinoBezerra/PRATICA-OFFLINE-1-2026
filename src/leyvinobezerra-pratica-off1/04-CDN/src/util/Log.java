package util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {
    }

    public static synchronized void info(String origem, String mensagem) {
        System.out.println("[" + LocalTime.now().format(FORMATO) + "] [" + origem + "] " + mensagem);
    }

    public static synchronized void erro(String origem, String mensagem) {
        System.err.println("[" + LocalTime.now().format(FORMATO) + "] [" + origem + "] ERRO: " + mensagem);
    }
}
