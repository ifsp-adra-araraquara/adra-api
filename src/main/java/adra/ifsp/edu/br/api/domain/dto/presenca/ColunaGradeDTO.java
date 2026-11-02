package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.TipoColunaGrade;

import java.time.LocalDate;

/**
 * Um dia de chamada do grupo na grade mensal. `rotulo` só vem em SEM_CHAMADA
 * — ex.: "Feriado nacional", "Passeio Lupo" — e é o texto que a grade mostra
 * na vertical, como na planilha.
 */
public record ColunaGradeDTO(
        LocalDate data,
        TipoColunaGrade tipo,
        String rotulo
) {
}
