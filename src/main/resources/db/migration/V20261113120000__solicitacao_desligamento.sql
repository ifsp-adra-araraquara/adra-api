-- Pedido da ADRA (13/11/2026): a Assistente Social não desliga atendido direto;
-- ela solicita e o Coordenador aprova (o atendido é inativado) ou recusa.
CREATE TABLE IF NOT EXISTS adra.solicitacao_desligamento (
    solicitacao_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assistido_id      bigint       NOT NULL REFERENCES adra.assistido (assistido_id) ON DELETE CASCADE,
    solicitado_por_id bigint       NOT NULL REFERENCES adra.usuario (usuario_id),
    data_saida        date         NOT NULL,
    motivo            varchar(500) NOT NULL,
    status            varchar(20)  NOT NULL DEFAULT 'PENDENTE',
    resposta          varchar(500),
    decidido_por_id   bigint       REFERENCES adra.usuario (usuario_id),
    decidido_em       timestamp with time zone,
    criado_em         timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_solicitacao_desligamento_status CHECK (status IN ('PENDENTE', 'APROVADA', 'RECUSADA', 'CANCELADA'))
);

-- Um pedido em aberto por atendido.
CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitacao_desligamento_pendente
    ON adra.solicitacao_desligamento (assistido_id) WHERE status = 'PENDENTE';
CREATE INDEX IF NOT EXISTS idx_solicitacao_desligamento_status
    ON adra.solicitacao_desligamento (status, criado_em DESC);

ALTER TABLE adra.solicitacao_desligamento ENABLE ROW LEVEL SECURITY;
