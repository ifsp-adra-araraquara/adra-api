CREATE TABLE adra.modulo (
    modulo_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nivel_permissao_id  BIGINT NOT NULL REFERENCES adra.nivel_permissao(nivel_permissao_id) ON DELETE CASCADE,
    codigo              VARCHAR(50) NOT NULL,
    nome_exibicao       VARCHAR(100) NOT NULL,
    rota                VARCHAR(150),
    icone               VARCHAR(50),
    ordem               INTEGER NOT NULL DEFAULT 0,
    eh_padrao           BOOLEAN NOT NULL DEFAULT FALSE,
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (nivel_permissao_id, codigo)
);
