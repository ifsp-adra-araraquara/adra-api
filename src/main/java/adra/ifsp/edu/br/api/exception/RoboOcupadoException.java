package adra.ifsp.edu.br.api.exception;

/** ADRAbot: outra consulta do robô está rodando e a espera passou do limite (503, "tente de novo"). */
public class RoboOcupadoException extends RuntimeException {
    public RoboOcupadoException(String mensagem) {
        super(mensagem);
    }
}
