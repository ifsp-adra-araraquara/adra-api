package adra.ifsp.edu.br.api.domain.enums;

/** Pedido de desligamento feito pela Assistente Social e decidido pelo Coordenador. */
public enum StatusSolicitacaoDesligamento {
    PENDENTE,
    APROVADA,
    RECUSADA,
    /** Retirado por quem pediu, ou encerrado porque o atendido já saiu por outro caminho. */
    CANCELADA
}
