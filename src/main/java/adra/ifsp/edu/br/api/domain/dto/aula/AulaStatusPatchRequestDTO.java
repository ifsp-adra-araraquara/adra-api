package adra.ifsp.edu.br.api.domain.dto.aula;

import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import jakarta.validation.constraints.NotNull;

public record AulaStatusPatchRequestDTO(
        @NotNull(message = "Informe o novo status da aula.")
        StatusAula statusAula
) {
}
