package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.time.LocalDate;

/**
 * Uma linha do histórico de frequência de um assistido (aba "Chamadas" do
 * cadastro) — uma aula REALIZADA de uma das turmas por onde ele passou, com
 * a presença dele naquele dia. `statusPresenca` vem PRESENTE por inferência
 * (modelo esparso: ausência de falta registrada = presente), igual ao GET
 * /api/chamadas/aula/{id}.
 */
public record FrequenciaAulaDTO(
        LocalDate dataAula,
        String nomeTurma,
        StatusPresenca statusPresenca
) {
}
