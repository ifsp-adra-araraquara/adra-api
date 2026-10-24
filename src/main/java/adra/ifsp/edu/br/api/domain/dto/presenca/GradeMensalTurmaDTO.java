package adra.ifsp.edu.br.api.domain.dto.presenca;

import java.util.List;

/**
 * Grade turma × dias de um mês, no formato da planilha de chamada que a
 * instituição usava (uma folha por mês, uma coluna por dia de aula, uma
 * linha por aluno). Só leitura — a chamada em si é lançada pela lista do
 * dia (GET/POST /api/chamadas/aula/{id}).
 */
public record GradeMensalTurmaDTO(
        Long turmaId,
        String nomeTurma,
        int ano,
        int mes,
        List<ColunaGradeDTO> colunas,
        List<AlunoGradeMensalDTO> alunos
) {
}
