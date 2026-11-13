package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.model.Notificacao;
import adra.ifsp.edu.br.api.domain.model.Usuario;

import java.time.Duration;
import java.util.List;

/**
 * FT-14: um canal por onde a notificação chega. O NotificacaoService não sabe
 * como cada canal entrega; canal novo (WhatsApp, push...) é só mais um bean.
 */
public interface CanalDeEntrega {

    CanalNotificacao canal();

    /** true: entrega quando a notificação nasce. false: espera o job juntar tudo em um resumo. */
    boolean imediato();

    /** Desligado por configuração: nada sai e as entregas continuam pendentes. */
    default boolean ligado() {
        return true;
    }

    /** Quantas mensagens ainda cabem hoje (limite do provedor). */
    default int vagasHoje() {
        return Integer.MAX_VALUE;
    }

    /** Pendente há mais tempo que isso não sai mais; null = não expira. */
    default Duration janela() {
        return null;
    }

    /**
     * Entrega de uma vez as notificações de um destinatário.
     *
     * @return false se não saiu; a entrega fica pendente e o job tenta de novo
     */
    boolean entregar(Usuario destinatario, List<Notificacao> notificacoes);
}
