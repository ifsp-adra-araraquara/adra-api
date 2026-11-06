package adra.ifsp.edu.br.api.domain.dto.chamada;

import adra.ifsp.edu.br.api.domain.enums.StatusChamada;

import java.time.LocalDate;

/** Situação da chamada de um grupo num dia: lançada, ou pendente (dia de chamada até hoje sem lançamento). */
public record StatusChamadaDiaDTO(
        Long turmaId,
        String nomeTurma,
        LocalDate data,
        StatusChamada status,
        String lancadaPorNome
) {
}
