package adra.ifsp.edu.br.api.domain.dto.notificacao;

import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.model.Notificacao.Envolvido;
import adra.ifsp.edu.br.api.domain.model.Usuario;

import java.util.Collection;
import java.util.Set;

/**
 * FT-14: o que se passa para {@code NotificacaoService.notificar}.
 *
 * @param tipo          o vocabulário dos avisos (FT-18), que o front já sabe exibir
 * @param referencia    identifica o fato dentro do tipo; o mesmo (tipo, referencia) não chega
 *                      duas vezes à mesma pessoa. Ex.: "assistido:12:desde:2026-11-03"
 * @param titulo        uma linha, com o nome de quem está envolvido
 * @param mensagem      o detalhe, opcional
 * @param link          rota do front onde a notificação se resolve, opcional
 * @param envolvidos    atendido, grupo, oficina... de quem a notificação fala
 * @param destinatarios cada um recebe a sua notificação; inativos são ignorados
 * @param canais        por onde chega: plataforma, e-mail ou os dois
 */
public record PedidoNotificacao(
        TipoAviso tipo,
        String referencia,
        String titulo,
        String mensagem,
        String link,
        Set<Envolvido> envolvidos,
        Collection<Usuario> destinatarios,
        Set<CanalNotificacao> canais
) {
    /** Plataforma e e-mail: o caso comum. */
    public static final Set<CanalNotificacao> TODOS_OS_CANAIS = Set.of(CanalNotificacao.PLATAFORMA, CanalNotificacao.EMAIL);
}
