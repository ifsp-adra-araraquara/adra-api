package adra.ifsp.edu.br.api.domain.dto.aula;

import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.model.Aula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AulaResponseDTO(
        Long aulaId,
        Long turmaId,
        String titulo,
        String descricao,
        LocalDate dataAula,
        LocalTime horarioInicio,
        LocalTime horarioFim,
        String conteudoPrevisto,
        String conteudoMinistrado,
        String objetivos,
        String recursosNecessarios,
        StatusAula statusAula,
        String observacoes,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {

    public static AulaResponseDTO fromEntity(Aula aula) {
        return new AulaResponseDTO(
                aula.getAulaId(),
                aula.getTurma() != null ? aula.getTurma().getTurmaId() : null,
                aula.getTitulo(),
                aula.getDescricao(),
                aula.getDataAula(),
                aula.getHorarioInicio(),
                aula.getHorarioFim(),
                aula.getConteudoPrevisto(),
                aula.getConteudoMinistrado(),
                aula.getObjetivos(),
                aula.getRecursosNecessarios(),
                aula.getStatusAula(),
                aula.getObservacoes(),
                aula.getCriadoEm(),
                aula.getAtualizadoEm()
        );
    }
}
