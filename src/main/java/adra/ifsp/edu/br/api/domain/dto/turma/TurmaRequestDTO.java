package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.util.List;

public record TurmaRequestDTO(

        @NotBlank(message = "Informe o nome do grupo.")
        @Size(max = 100, message = "O nome do grupo deve ter no máximo 100 caracteres.")
        String nomeTurma,

        @NotNull(message = "Selecione o turno.")
        Turno turno,

        Long faixaEtariaId,

        @NotNull(message = "Informe a capacidade do grupo.")
        @Positive(message = "A capacidade deve ser maior que zero.")
        Integer capacidade,

        String observacoes,

        List<DayOfWeek> diasDaSemana,

        List<Long> oficinaIds
) {
}
