package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Turma;

public record TurmaResponseDTO(
        Long turmaId,
        String nomeTurma,
        Turno turno,
        String faixaEtaria,
        Integer capacidade,
        Boolean ativo,
        String observacoes
) {

    /**
     * Fábrica de conveniência para converter a entidade em DTO de resposta,
     * evitando expor a entidade JPA diretamente pelo controller.
     */
    public static TurmaResponseDTO fromEntity(Turma turma) {
        return new TurmaResponseDTO(
                turma.getTurmaId(),
                turma.getNomeTurma(),
                turma.getTurno(),
                turma.getFaixaEtaria(),
                turma.getCapacidade(),
                turma.getAtivo(),
                turma.getObservacoes()
        );
    }
}
