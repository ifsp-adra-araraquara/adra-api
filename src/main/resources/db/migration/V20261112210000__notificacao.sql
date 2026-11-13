-- FT-14: notificações guardadas. Uma linha por destinatário: o mesmo fato
-- avisado a 3 Coordenadores vira 3 notificações, cada uma com a sua leitura.
-- (tipo, referencia, destinatario_id) é único: é isso que impede avisar duas
-- vezes o mesmo fato para a mesma pessoa.
CREATE TABLE IF NOT EXISTS adra.notificacao (
    notificacao_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo             varchar(40)  NOT NULL,
    referencia       varchar(120) NOT NULL,
    destinatario_id  bigint       NOT NULL REFERENCES adra.usuario (usuario_id),
    titulo           varchar(200) NOT NULL,
    mensagem         varchar(500),
    link             varchar(255),
    criado_em        timestamp with time zone NOT NULL DEFAULT now(),
    lida_em          timestamp with time zone,
    CONSTRAINT uq_notificacao_tipo_referencia_destinatario UNIQUE (tipo, referencia, destinatario_id)
);

CREATE INDEX IF NOT EXISTS idx_notificacao_destinatario
    ON adra.notificacao (destinatario_id, criado_em DESC);

-- Quem a notificação envolve (atendido, responsável, grupo, oficina, usuário).
-- Sem FK: o id aponta para tabelas diferentes conforme o tipo.
CREATE TABLE IF NOT EXISTS adra.notificacao_envolvido (
    notificacao_id  bigint      NOT NULL REFERENCES adra.notificacao (notificacao_id) ON DELETE CASCADE,
    tipo_envolvido  varchar(20) NOT NULL,
    envolvido_id    bigint      NOT NULL,
    PRIMARY KEY (notificacao_id, tipo_envolvido, envolvido_id)
);

CREATE INDEX IF NOT EXISTS idx_notificacao_envolvido
    ON adra.notificacao_envolvido (tipo_envolvido, envolvido_id);

-- Uma entrega por canal (PLATAFORMA, EMAIL). O e-mail sai no resumo diário,
-- por isso fica PENDENTE até o job passar.
CREATE TABLE IF NOT EXISTS adra.notificacao_entrega (
    entrega_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notificacao_id  bigint      NOT NULL REFERENCES adra.notificacao (notificacao_id) ON DELETE CASCADE,
    canal           varchar(20) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'PENDENTE',
    enviada_em      timestamp with time zone,
    CONSTRAINT uq_notificacao_entrega_canal UNIQUE (notificacao_id, canal)
);

CREATE INDEX IF NOT EXISTS idx_notificacao_entrega_pendente
    ON adra.notificacao_entrega (canal) WHERE status = 'PENDENTE';

ALTER TABLE adra.notificacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE adra.notificacao_envolvido ENABLE ROW LEVEL SECURITY;
ALTER TABLE adra.notificacao_entrega ENABLE ROW LEVEL SECURITY;
