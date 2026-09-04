package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.*;

public record TurmaRequestDTO(

        @NotBlank(message = "Informe o nome da turma.")
        @Size(max = 100, message = "O nome da turma deve ter no máximo 100 caracteres.")
        String nomeTurma,

        @NotNull(message = "Selecione o turno.")
        Turno turno,

        @Size(max = 50, message = "A faixa etária deve ter no máximo 50 caracteres.")
        String faixaEtaria,

        @NotNull(message = "Informe a capacidade da turma.")
        @Positive(message = "A capacidade deve ser maior que zero.")
        Integer capacidade,

        String observacoes
) {
}
