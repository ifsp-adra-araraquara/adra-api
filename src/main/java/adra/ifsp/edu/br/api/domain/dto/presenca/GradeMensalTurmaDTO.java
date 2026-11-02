package adra.ifsp.edu.br.api.domain.dto.presenca;

import java.util.List;

/**
 * Grade turma × dias de um mês, no formato da planilha de chamada que a
 * instituição usava (uma folha por mês, uma coluna por dia de chamada, uma
 * linha por atendido). Só leitura — a chamada em si é lançada pela lista do
 * dia (GET/PUT /api/chamadas/turma/{id}/dia/{data}).
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
