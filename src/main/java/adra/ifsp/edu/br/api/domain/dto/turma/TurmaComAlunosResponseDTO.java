package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
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

    /** Uma linha por grupo atendido numa oficina do oficineiro. */
    public static TurmaComAlunosResponseDTO fromVinculo(OficinaTurma vinculo, long quantidadeAlunos) {
        Turma turma = vinculo.getTurma();
        return new TurmaComAlunosResponseDTO(
                turma.getTurmaId(),
                turma.getNomeTurma(),
                vinculo.getOficina().getOficinaId(),
                vinculo.getOficina().getNomeOficina(),
                turma.getTurno(),
                (int) quantidadeAlunos,
                turma.getCapacidade()
        );
    }
}
