package adra.ifsp.edu.br.api.domain.dto.oficina;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Números dos grupos de uma oficina para o card da listagem.
 *
 * @param turmasAtivas grupos ativos atendidos pela oficina
 * @param alunos       alunos nos grupos ativos hoje (vagas ocupadas)
 * @param capacidade   soma das capacidades dos grupos ativos
 * @param horarios     combinações de dias dos grupos ativos, a mais
 *                     comum primeiro
 */
public record OficinaResumoDTO(
        Long oficinaId,
        int turmasAtivas,
        long alunos,
        int capacidade,
        List<Horario> horarios
) {

    /** Dias ordenados de segunda a domingo; {@code turmas} = quantos grupos usam esses dias. */
    public record Horario(List<DayOfWeek> dias, int turmas) {
    }
}
