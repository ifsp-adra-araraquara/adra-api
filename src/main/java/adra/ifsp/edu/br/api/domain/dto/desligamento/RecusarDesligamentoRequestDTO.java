package adra.ifsp.edu.br.api.domain.dto.desligamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecusarDesligamentoRequestDTO(
        @NotBlank(message = "Informe o motivo da recusa.")
        @Size(max = 500, message = "O motivo pode ter no máximo 500 caracteres.") String resposta
) {
}
