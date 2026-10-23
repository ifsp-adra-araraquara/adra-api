package adra.ifsp.edu.br.api.domain.dto.aula;

import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.model.Aula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AulaComDetalhesResponseDTO(
        Long aulaId,
        Long turmaId,
        String nomeTurma,
        String nomeOficineiro,
        Integer quantidadeAlunos,
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

    public static AulaComDetalhesResponseDTO fromEntity(Aula aula, Integer quantidadeAlunos) {
        return new AulaComDetalhesResponseDTO(
                aula.getAulaId(),
                aula.getTurma() != null ? aula.getTurma().getTurmaId() : null,
                aula.getTurma() != null ? aula.getTurma().getNomeTurma() : null,
                nomeOficineiroDaAula(aula),
                quantidadeAlunos,
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

    /** Oficineiro gravado na aula; aulas sem esse dado (antigas) caem no da turma. */
    private static String nomeOficineiroDaAula(Aula aula) {
        if (aula.getOficineiro() != null) {
            return aula.getOficineiro().getNomeCompleto();
        }
        return aula.getTurma() != null && aula.getTurma().getOficineiroResponsavel() != null
                ? aula.getTurma().getOficineiroResponsavel().getNomeCompleto() : null;
    }
}
