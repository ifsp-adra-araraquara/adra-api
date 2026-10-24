package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PeriodoSuspensaoRequestDTO(
        @NotNull(message = "Informe o início da suspensão.")
        LocalDate inicio,

        @NotNull(message = "Informe o fim da suspensão.")
        LocalDate fim
) {
}
