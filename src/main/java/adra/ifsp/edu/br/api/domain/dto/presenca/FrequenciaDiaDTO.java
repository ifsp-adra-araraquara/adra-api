package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.time.LocalDate;

/**
 * Uma linha do histórico de frequência de um atendido (aba "Chamadas" da ficha):
 * uma chamada lançada de um grupo por onde ele passou, com a presença dele no dia.
 * PRESENTE é inferido (modelo esparso: sem falta registrada = presente).
 */
public record FrequenciaDiaDTO(
        LocalDate data,
        Long turmaId,
        String nomeTurma,
        StatusPresenca statusPresenca
) {
}
