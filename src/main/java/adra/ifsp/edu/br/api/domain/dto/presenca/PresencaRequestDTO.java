package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import jakarta.validation.constraints.NotNull;

public record PresencaRequestDTO(

        @NotNull(message = "Informe a aula.")
        Long aulaId,

        @NotNull(message = "Informe o assistido.")
        Long assistidoId,

        @NotNull(message = "Informe o status da presença.")
        StatusPresenca statusPresenca,

        String justificativaFalta,

        String observacaoDoDia
) {
}
