package adra.ifsp.edu.br.api.domain.enums;

/** FT-14: por onde uma notificação chega. Canal novo = valor novo + um CanalDeEntrega. */
public enum CanalNotificacao {
    /** O sino e a tela de notificações do sistema. */
    PLATAFORMA,
    /** Resumo diário por e-mail (Brevo). */
    EMAIL
}
