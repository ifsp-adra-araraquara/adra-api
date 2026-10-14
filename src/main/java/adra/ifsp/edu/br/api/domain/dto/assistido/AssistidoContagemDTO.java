package adra.ifsp.edu.br.api.domain.dto.assistido;

/** Totais das abas da listagem de assistidos, respeitando busca/turma/oficina. */
public record AssistidoContagemDTO(
        long todos,
        long ativos,
        long inativos,
        long emAcompanhamento
) {
}
