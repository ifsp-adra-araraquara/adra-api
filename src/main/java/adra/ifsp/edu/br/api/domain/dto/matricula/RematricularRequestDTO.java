package adra.ifsp.edu.br.api.domain.dto.matricula;

import jakarta.validation.constraints.NotNull;

/** Rematrícula: não há passagem automática de grupo, a equipe escolhe. */
public record RematricularRequestDTO(
        @NotNull(message = "Escolha o grupo do atendido no novo ano.")
        Long turmaId
) {
}
