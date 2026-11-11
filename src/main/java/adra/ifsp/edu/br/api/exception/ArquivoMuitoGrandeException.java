package adra.ifsp.edu.br.api.exception;

import java.util.Locale;

/** Arquivo acima do limite (da categoria ou do bucket). Vira 413 com a mensagem pronta para o usuário. */
public class ArquivoMuitoGrandeException extends ArquivoInvalidoException {

    public ArquivoMuitoGrandeException(String mensagem) {
        super(mensagem);
    }

    /** "Arquivo muito grande (2,4 MB). O limite é 2 MB." — com dica para PDF, que não é recomprimido. */
    public static ArquivoMuitoGrandeException de(long tamanhoBytes, long limiteBytes, boolean pdf) {
        return new ArquivoMuitoGrandeException("Arquivo muito grande (" + formatar(tamanhoBytes) + "). O limite é "
                + formatar(limiteBytes) + "." + (pdf ? " Se possível, envie uma foto do documento." : ""));
    }

    /** Quando só o limite é conhecido (ex.: recusa do storage ou do multipart). */
    public static ArquivoMuitoGrandeException acimaDe(long limiteBytes) {
        return new ArquivoMuitoGrandeException("Arquivo muito grande. O limite é " + formatar(limiteBytes)
                + " por arquivo.");
    }

    static String formatar(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 1) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return mb == Math.floor(mb)
                ? String.format(Locale.ROOT, "%d MB", (long) mb)
                : String.format(Locale.of("pt", "BR"), "%.1f MB", mb);
    }
}
