package adra.ifsp.edu.br.api.domain.dto.matricula;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Rematrícula: não há passagem automática de grupo, a equipe escolhe.
 * {@code ficha}: dados cadastrais revisados na tela de avaliação (o que veio
 * do formulário e o que a equipe corrigiu). Null = a ficha fica como está.
 * CPF, grupo, data de entrada, termos e responsáveis não mudam por aqui.
 */
public record RematricularRequestDTO(
        @NotNull(message = "Escolha o grupo do atendido no novo ano.")
        Long turmaId,

        @Valid
        AssistidoRequestDTO ficha
) {
}
