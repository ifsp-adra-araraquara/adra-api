package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MotivoDisciplinarRequestDTO(
        @NotBlank(message = "Informe o nome do motivo.")
        @Size(max = 150, message = "O nome do motivo deve ter no máximo 150 caracteres.")
        String nome,

        @NotNull(message = "Informe o nível do motivo.")
        NivelDisciplinar nivel,

        /** Null mantém como está (ativo, para motivo novo). */
        Boolean ativo
) {
}
