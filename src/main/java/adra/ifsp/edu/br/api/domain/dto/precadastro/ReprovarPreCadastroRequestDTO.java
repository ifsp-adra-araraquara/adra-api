package adra.ifsp.edu.br.api.domain.dto.precadastro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReprovarPreCadastroRequestDTO(
        @NotBlank(message = "Informe o motivo da reprovação.")
        @Size(max = 1000)
        String motivo
) {
}
