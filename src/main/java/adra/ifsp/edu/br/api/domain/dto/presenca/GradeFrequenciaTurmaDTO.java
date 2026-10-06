package adra.ifsp.edu.br.api.domain.dto.presenca;

import java.time.LocalDate;
import java.util.List;

/**
 * Grade turma × dias (estilo planilha) pra análise calma — "Ver grade da
 * turma" a partir da chamada. Não é pro fluxo rápido de marcar presença (ver
 * `PresencaResponseDTO.historicoRecente` pra isso); é uma tela separada,
 * carregada só sob demanda.
 *
 * `datas` e `presencas` de cada aluno são paralelos (mesmo índice = mesma
 * aula), mais recente primeiro.
 */
public record GradeFrequenciaTurmaDTO(
        List<LocalDate> datas,
        List<GradeAlunoDTO> alunos
) {
}
