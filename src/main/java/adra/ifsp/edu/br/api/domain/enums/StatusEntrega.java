package adra.ifsp.edu.br.api.domain.enums;

/** FT-14: situação da entrega de uma notificação em um canal. */
public enum StatusEntrega {
    PENDENTE,
    ENVIADA,
    /** Não sai mais: destinatário inativo ou pendente há mais tempo que a janela do canal. */
    DESCARTADA
}
