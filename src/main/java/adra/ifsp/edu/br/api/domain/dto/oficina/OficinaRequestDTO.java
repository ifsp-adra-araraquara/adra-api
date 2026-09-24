package adra.ifsp.edu.br.api.domain.dto.oficina;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OficinaRequestDTO(

        @NotBlank(message = "Informe o nome da oficina.")
        @Size(max = 100, message = "O nome da oficina deve ter no máximo 100 caracteres.")
        String nomeOficina,

        @NotNull(message = "Informe o oficineiro responsável.")
        Long oficineiroResponsavelId
) {
}
