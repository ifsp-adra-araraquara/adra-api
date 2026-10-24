package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.TipoColunaGrade;

import java.time.LocalDate;

/**
 * Um dia da grade mensal. `aulaId` é null em SEM_AULA (feriado/recesso não
 * gera aula). `rotulo` só vem em CANCELADA/SEM_AULA — ex.: "Passeio Lupo",
 * "Feriado" — e é o texto que a grade mostra na vertical, como na planilha.
 */
public record ColunaGradeDTO(
        LocalDate data,
        Long aulaId,
        TipoColunaGrade tipo,
        String rotulo
) {
}
