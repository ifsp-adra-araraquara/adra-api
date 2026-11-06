package adra.ifsp.edu.br.api.exception;

/**
 * CPF do atendido já cadastrado (ao aprovar um pré-cadastro). Vai com o id do
 * atendido existente para o front oferecer abrir a ficha dele.
 */
public class AssistidoJaCadastradoException extends RuntimeException {

    private final Long assistidoId;

    public AssistidoJaCadastradoException(Long assistidoId, String nomeCompleto) {
        super("CPF já cadastrado para o atendido " + nomeCompleto + ".");
        this.assistidoId = assistidoId;
    }

    public Long getAssistidoId() {
        return assistidoId;
    }
}
