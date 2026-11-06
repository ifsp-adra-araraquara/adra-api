package adra.ifsp.edu.br.api.domain.dto.chamada;

import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import jakarta.validation.constraints.NotNull;

/** Um atendido no PUT da chamada do grupo. Motivo só vale para FALTA_JUSTIFICADA. */
public record PresencaChamadaRequestDTO(

        @NotNull(message = "Informe o atendido.")
        Long assistidoId,

        @NotNull(message = "Informe o status da presença.")
        StatusPresenca statusPresenca,

        MotivoFalta motivoFalta,

        String observacao
) {
}
