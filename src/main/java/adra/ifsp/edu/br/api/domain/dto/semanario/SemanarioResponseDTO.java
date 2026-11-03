package adra.ifsp.edu.br.api.domain.dto.semanario;

import adra.ifsp.edu.br.api.domain.model.Semanario;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SemanarioResponseDTO(
        Long semanarioId,
        Long oficinaId,
        String nomeOficina,
        Long turmaId,
        String nomeTurma,
        LocalDate semanaInicio,
        String conteudoPrevisto,
        String objetivos,
        String recursosNecessarios,
        String nomeEnviadoPor,
        LocalDateTime atualizadoEm
) {

    public static SemanarioResponseDTO de(Semanario semanario) {
        return new SemanarioResponseDTO(
                semanario.getSemanarioId(),
                semanario.getOficina().getOficinaId(),
                semanario.getOficina().getNomeOficina(),
                semanario.getTurma().getTurmaId(),
                semanario.getTurma().getNomeTurma(),
                semanario.getSemanaInicio(),
                semanario.getConteudoPrevisto(),
                semanario.getObjetivos(),
                semanario.getRecursosNecessarios(),
                semanario.getEnviadoPor().getNomeCompleto(),
                semanario.getAtualizadoEm()
        );
    }
}
