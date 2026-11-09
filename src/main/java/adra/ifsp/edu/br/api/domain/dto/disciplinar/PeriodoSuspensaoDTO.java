package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import java.time.LocalDate;

/**
 * Período de uma suspensão contado em dias de atividade do grupo do atendido:
 * de `inicio` a `fim` (inclusive) há `diasDeAtividade` dias com atividade, e ele
 * volta no `retorno`, o próximo dia de atividade depois do fim.
 */
public record PeriodoSuspensaoDTO(
        LocalDate inicio,
        LocalDate fim,
        int diasDeAtividade,
        LocalDate retorno
) {
}
