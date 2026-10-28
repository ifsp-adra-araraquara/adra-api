package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.time.LocalDate;
import java.util.List;

/**
 * Uma linha da grade mensal. `presencas` é paralela a
 * `GradeMensalTurmaDTO.colunas` (mesmo índice = mesmo dia) e só tem valor
 * nas colunas AULA em que o aluno estava na turma e não desligado; nas
 * demais vem null. `dataSaida`/`motivoSaida` vêm preenchidos quando o
 * assistido foi desligado — a grade mostra a faixa "Desligado" a partir daí.
 */
public record AlunoGradeMensalDTO(
        Long assistidoId,
        String nomeCompleto,
        LocalDate dataNascimento,
        LocalDate dataSaida,
        String motivoSaida,
        List<StatusPresenca> presencas
) {
}
