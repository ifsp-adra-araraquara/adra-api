package adra.ifsp.edu.br.api.domain.dto.usuario;

import jakarta.validation.constraints.NotNull;

public record UsuarioStatusRequestDTO(
        @NotNull(message = "Status (ativo) e' obrigatorio")
        Boolean ativo
) {
}
