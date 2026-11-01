package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import jakarta.validation.constraints.NotBlank;

/** Sem nível, zera todos os níveis. */
public record ZerarContagemRequestDTO(
        @NotBlank(message = "Informe o motivo.")
        String motivo,

        NivelDisciplinar nivel
) {
}
