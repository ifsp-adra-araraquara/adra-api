package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Turma;

import java.time.DayOfWeek;
import java.util.List;

public record TurmaResponseDTO(
        Long turmaId,
        String nomeTurma,
        Turno turno,
        String faixaEtaria,
        Integer capacidade,
        Boolean ativo,
        String observacoes,
        Integer quantidadeAlunos,
        List<DayOfWeek> diasDaSemana,
        List<Long> oficinaIds
) {

    /**
     * Quantidade de matriculados e oficinas já resolvidas pelo chamador (ver
     * TurmaService#listarComFiltros — contagem e oficinas em lote, não uma
     * query por turma). Quantidade nula nos endpoints que não precisam dela.
     */
    public static TurmaResponseDTO fromEntity(Turma turma, Integer quantidadeAlunos, List<Long> oficinaIds) {
        return new TurmaResponseDTO(
                turma.getTurmaId(),
                turma.getNomeTurma(),
                turma.getTurno(),
                turma.getFaixaEtaria(),
                turma.getCapacidade(),
                turma.getAtivo(),
                turma.getObservacoes(),
                quantidadeAlunos,
                turma.getDiasDaSemana() != null ? List.copyOf(turma.getDiasDaSemana()) : List.of(),
                oficinaIds
        );
    }
}
