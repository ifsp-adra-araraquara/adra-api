package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.time.LocalDate;

/**
 * Um dia do histórico recente de um aluno, embutido no roster de
 * `GET /api/chamadas/aula/{id}` — ver `PresencaResponseDTO.historicoRecente`.
 * Mais recente primeiro. `statusPresenca` PRESENTE é inferido (modelo
 * esparso), igual ao resto do domínio de presença.
 */
public record PresencaHistoricoDTO(
        LocalDate dataAula,
        StatusPresenca statusPresenca
) {
}
