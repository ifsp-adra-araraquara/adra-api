package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import jakarta.validation.constraints.NotNull;

public record PresencaRequestDTO(

        @NotNull(message = "Informe a aula.")
        Long aulaId,

        @NotNull(message = "Informe o assistido.")
        Long assistidoId,

        @NotNull(message = "Informe o status da presença.")
        StatusPresenca statusPresenca,

        // obrigatório apenas quando statusPresenca = FALTA_JUSTIFICADA (CA-65.2).
        // Não dá pra expressar essa regra condicional só com @NotNull — validação
        // fica no PresencaMapper.sincronizarFaltaJustificada.
        MotivoFalta motivoFalta,

        // obrigatório apenas quando motivoFalta = OUTRO (CA-65.2)
        String observacao
) {
}