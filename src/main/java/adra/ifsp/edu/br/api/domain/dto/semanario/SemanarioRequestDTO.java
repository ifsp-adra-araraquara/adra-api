package adra.ifsp.edu.br.api.domain.dto.semanario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Qualquer dia da semana serve: o semanário fica gravado na segunda-feira dela. */
public record SemanarioRequestDTO(

        @NotNull(message = "Informe a oficina.")
        Long oficinaId,

        @NotNull(message = "Informe o grupo.")
        Long turmaId,

        @NotNull(message = "Informe a semana.")
        LocalDate semana,

        @NotBlank(message = "Descreva os conteúdos da semana.")
        String conteudoPrevisto,

        String objetivos,

        String recursosNecessarios
) {
}
