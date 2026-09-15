package adra.ifsp.edu.br.api.domain.dto.oficina;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OficinaRequestDTO(

        @NotBlank(message = "Informe o nome da oficina.")
        @Size(max = 100, message = "O nome da oficina deve ter no máximo 100 caracteres.")
        String nomeOficina,

        @NotBlank(message = "Informe o oficineiro responsável.")
        @Size(max = 150, message = "O nome do oficineiro responsável deve ter no máximo 150 caracteres.")
        String oficineiroResponsavel
) {
}
