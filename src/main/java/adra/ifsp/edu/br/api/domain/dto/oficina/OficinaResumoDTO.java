package adra.ifsp.edu.br.api.domain.dto.oficina;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Números das turmas de uma oficina para o card da listagem.
 *
 * @param turmasAtivas turmas ativas da oficina
 * @param alunos       alunos nas turmas ativas hoje (vagas ocupadas)
 * @param capacidade   soma das capacidades das turmas ativas
 * @param horarios     combinações de dias + horário das turmas ativas, a
 *                     mais comum primeiro
 */
public record OficinaResumoDTO(
        Long oficinaId,
        int turmasAtivas,
        long alunos,
        int capacidade,
        List<Horario> horarios
) {

    /** Dias ordenados de segunda a domingo; {@code turmas} = quantas turmas usam esse horário. */
    public record Horario(List<DayOfWeek> dias, LocalTime inicio, int turmas) {
    }
}
