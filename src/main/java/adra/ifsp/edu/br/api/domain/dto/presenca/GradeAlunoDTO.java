package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.util.List;

/** Uma linha da grade de frequência da turma — ver GradeFrequenciaTurmaDTO. */
public record GradeAlunoDTO(
        Long assistidoId,
        String nomeCompleto,
        List<StatusPresenca> presencas
) {
}
