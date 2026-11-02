package adra.ifsp.edu.br.api.domain.dto.nota;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record NotaCalendarioRequestDTO(

        @NotNull(message = "Informe a data da nota.")
        LocalDate dataNota,

        LocalTime horario,

        @NotBlank(message = "Informe o título da nota.")
        @Size(max = 120, message = "O título deve ter no máximo 120 caracteres.")
        String titulo,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String descricao
) {
}
