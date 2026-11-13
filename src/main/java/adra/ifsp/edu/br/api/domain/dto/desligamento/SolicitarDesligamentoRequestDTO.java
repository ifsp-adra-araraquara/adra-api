package adra.ifsp.edu.br.api.domain.dto.desligamento;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SolicitarDesligamentoRequestDTO(
        @NotNull(message = "Informe a data de saída.") LocalDate dataSaida,
        @NotBlank(message = "Informe o motivo do desligamento.")
        @Size(max = 500, message = "O motivo pode ter no máximo 500 caracteres.") String motivo
) {
}
