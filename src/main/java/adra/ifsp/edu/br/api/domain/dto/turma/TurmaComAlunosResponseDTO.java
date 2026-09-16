package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Turma;

/**
 * Versão de TurmaResponseDTO enriquecida com o nome da oficina e a
 * quantidade de alunos ativos — usada nas telas onde o oficineiro
 * precisa ver isso de cara (ex.: "Minhas turmas"), sem ter que fazer
 * uma chamada extra por turma pra resolver oficinaId/contar alunos.
 */
public record TurmaComAlunosResponseDTO(
        Long turmaId,
        String nomeTurma,
        Long oficinaId,
        String nomeOficina,
        Turno turno,
        Integer quantidadeAlunos,
        Integer capacidade
) {

    public static TurmaComAlunosResponseDTO fromEntity(Turma turma, long quantidadeAlunos) {
        return new TurmaComAlunosResponseDTO(
                turma.getTurmaId(),
                turma.getNomeTurma(),
                turma.getOficina() != null ? turma.getOficina().getOficinaId() : null,
                turma.getOficina() != null ? turma.getOficina().getNomeOficina() : null,
                turma.getTurno(),
                (int) quantidadeAlunos,
                turma.getCapacidade()
        );
    }
}
