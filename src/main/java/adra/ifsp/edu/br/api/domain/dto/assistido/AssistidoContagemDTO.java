package adra.ifsp.edu.br.api.domain.dto.assistido;

/** Totais das abas da listagem de assistidos, respeitando busca/turma/oficina. */
public record AssistidoContagemDTO(
        long todos,
        long ativos,
        long inativos,
        long emAcompanhamento,
        /** Matriculados para o ano seguinte que ainda não começaram (fora das outras abas). */
        long proximoAno,
        /** Ano em que eles começam (null se não há nenhum). */
        Integer anoProximo
) {
}
