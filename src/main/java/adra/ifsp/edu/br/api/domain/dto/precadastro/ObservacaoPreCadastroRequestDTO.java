package adra.ifsp.edu.br.api.domain.dto.precadastro;

import jakarta.validation.constraints.Size;

public record ObservacaoPreCadastroRequestDTO(
        @Size(max = 2000, message = "A observação pode ter no máximo 2000 caracteres.")
        String observacao
) {
}
