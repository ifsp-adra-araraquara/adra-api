package adra.ifsp.edu.br.api.exception;

/** Falha ao falar com o storage (Supabase/R2). Vira 502. */
public class ArmazenamentoException extends RuntimeException {
    public ArmazenamentoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}