package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.model.Notificacao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.springframework.stereotype.Component;

import java.util.List;

/** FT-14: a linha gravada já é o que aparece no sino; não há o que enviar. */
@Component
public class CanalPlataforma implements CanalDeEntrega {

    @Override
    public CanalNotificacao canal() {
        return CanalNotificacao.PLATAFORMA;
    }

    @Override
    public boolean imediato() {
        return true;
    }

    @Override
    public boolean entregar(Usuario destinatario, List<Notificacao> notificacoes) {
        return true;
    }
}
