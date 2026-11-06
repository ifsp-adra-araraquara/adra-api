package adra.ifsp.edu.br.api.domain.dto.chamada;

import adra.ifsp.edu.br.api.domain.enums.StatusChamada;
import adra.ifsp.edu.br.api.domain.enums.Turno;

import java.time.LocalDateTime;

public record GrupoDoDiaDTO(
        Long turmaId,
        String nomeTurma,
        Turno turno,
        int totalAtendidos,
        StatusChamada status,
        String lancadaPorNome,
        LocalDateTime lancadaEm
) {
}
