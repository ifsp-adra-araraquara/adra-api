package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDateTime;
import java.util.List;

/** Robozinho ADRA — registros disciplinares (não arquivados) do mês por grupo, com o mês anterior. */
public record DisciplinarResumoRoboDTO(String mes, long ocorrencias, long advertencias, long suspensoes,
                                       long totalMesAnterior, LocalDateTime geradoEm, List<Grupo> grupos) {

    public record Grupo(Long turmaId, String nomeTurma, long ocorrencias, long advertencias, long suspensoes,
                        long totalMesAnterior) {
    }
}
