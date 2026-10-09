package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Turma;

public record TurmaResponseDTO(
        Long turmaId,
        Long oficinaId,
        Long oficineiroResponsavelId,
        String nomeTurma,
        Turno turno,
        String faixaEtaria,
        Integer capacidade,
        Boolean ativo,
        String observacoes,
        Integer quantidadeAlunos
) {

    /**
     * Fábrica de conveniência para converter a entidade em DTO de resposta,
     * evitando expor a entidade JPA diretamente pelo controller. Sem
     * contagem de matriculados resolvida (ver overload abaixo) — usado nos
     * endpoints de cadastro/edição/status, que não precisam disso.
     */
    public static TurmaResponseDTO fromEntity(Turma turma) {
        return fromEntity(turma, null);
    }

    /**
     * Mesma conversão, mas com a quantidade de matriculados já resolvida
     * pelo chamador (ver TurmaService#listarComFiltros — contagem em lote
     * via AssistidoRepository#contarAtivosPorTurma, não uma query por turma).
     */
    public static TurmaResponseDTO fromEntity(Turma turma, Integer quantidadeAlunos) {
        return new TurmaResponseDTO(
                turma.getTurmaId(),
                turma.getOficina() != null ? turma.getOficina().getOficinaId() : null,
                turma.getOficineiroResponsavel() != null ? turma.getOficineiroResponsavel().getUsuarioId() : null,
                turma.getNomeTurma(),
                turma.getTurno(),
                turma.getFaixaEtaria(),
                turma.getCapacidade(),
                turma.getAtivo(),
                turma.getObservacoes(),
                quantidadeAlunos
        );
    }
}
