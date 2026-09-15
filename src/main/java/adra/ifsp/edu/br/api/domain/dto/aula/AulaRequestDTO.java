package adra.ifsp.edu.br.api.domain.dto.aula;

import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AulaRequestDTO(

        @NotNull(message = "Informe a turma.")
        Long turmaId,

        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        String descricao,

        @NotNull(message = "Informe a data da aula.")
        LocalDate dataAula,

        LocalTime horarioInicio,

        LocalTime horarioFim,

        String conteudoPrevisto,

        String conteudoMinistrado,

        String objetivos,

        String recursosNecessarios,

        StatusAula statusAula,

        String observacoes
) {
}
