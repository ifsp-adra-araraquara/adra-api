package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDateTime;
import java.util.List;

/** Robozinho ADRA — frequência por dia da semana (1 = segunda … 7 = domingo). */
public record FrequenciaDiaSemanaDTO(String de, String ate, Long turmaId, LocalDateTime geradoEm, List<Dia> dias) {

    public record Dia(int diaSemana, long chamadas, long esperados, long ausencias, Integer percentual) {
    }
}
