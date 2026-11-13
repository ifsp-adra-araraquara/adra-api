package adra.ifsp.edu.br.api.domain.dto.notificacao;

/**
 * FT-14: como está o envio por e-mail (GET /api/notificacoes/envio, só Administrador).
 * {@code enviadosHoje} conta mensagens (um resumo = uma), não notificações.
 */
public record SituacaoEnvioDTO(
        boolean emailLigado,
        int limiteDiario,
        long enviadosHoje,
        long pendentes
) {
}
