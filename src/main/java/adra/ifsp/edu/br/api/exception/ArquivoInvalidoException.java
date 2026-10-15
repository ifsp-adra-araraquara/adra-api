package adra.ifsp.edu.br.api.exception;

/** Arquivo recusado: tipo não permitido, corrompido ou grande demais. Vira 400. */
public class ArquivoInvalidoException extends RuntimeException {
    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }
}