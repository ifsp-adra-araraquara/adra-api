package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import jakarta.validation.constraints.NotBlank;

public record ArquivarRegistroRequestDTO(
        @NotBlank(message = "Informe o motivo do arquivamento.")
        String motivo
) {
}
