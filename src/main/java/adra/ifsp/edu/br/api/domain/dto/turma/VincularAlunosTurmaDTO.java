package adra.ifsp.edu.br.api.domain.dto.turma;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Corpo do POST /api/turmas/{id}/alunos — botão "+ Vincular alunos" da tela de turmas. */
public record VincularAlunosTurmaDTO(
        @NotEmpty(message = "Selecione pelo menos um assistido.")
        List<Long> assistidoIds
) {
}
