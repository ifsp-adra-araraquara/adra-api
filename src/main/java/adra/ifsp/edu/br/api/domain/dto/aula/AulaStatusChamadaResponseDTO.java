package adra.ifsp.edu.br.api.domain.dto.aula;

import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusChamada;
import adra.ifsp.edu.br.api.domain.model.Aula;

import java.time.LocalDate;
import java.time.LocalTime;

public record AulaStatusChamadaResponseDTO(
        Long aulaId,
        Long turmaId,
        String nomeTurma,
        String titulo,
        LocalDate dataAula,
        LocalTime horarioInicio,
        LocalTime horarioFim,
        StatusAula statusAula,
        StatusChamada statusChamada
) {

    public static AulaStatusChamadaResponseDTO fromEntity(Aula aula, StatusChamada statusChamada) {
        return new AulaStatusChamadaResponseDTO(
                aula.getAulaId(),
                aula.getTurma() != null ? aula.getTurma().getTurmaId() : null,
                aula.getTurma() != null ? aula.getTurma().getNomeTurma() : null,
                aula.getTitulo(),
                aula.getDataAula(),
                aula.getHorarioInicio(),
                aula.getHorarioFim(),
                aula.getStatusAula(),
                statusChamada
        );
    }
}
