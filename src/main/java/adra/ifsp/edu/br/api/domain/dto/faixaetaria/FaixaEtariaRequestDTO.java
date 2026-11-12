package adra.ifsp.edu.br.api.domain.dto.faixaetaria;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FaixaEtariaRequestDTO(
        @NotNull(message = "Informe a idade mínima.")
        @Min(value = 0, message = "A idade mínima não pode ser negativa.")
        @Max(value = 120, message = "A idade mínima deve ser no máximo 120.")
        Short idadeMinima,

        @NotNull(message = "Informe a idade máxima.")
        @Min(value = 0, message = "A idade máxima não pode ser negativa.")
        @Max(value = 120, message = "A idade máxima deve ser no máximo 120.")
        Short idadeMaxima,

        /** Null mantém como está (ativa, para faixa nova). */
        Boolean ativo
) {
}
