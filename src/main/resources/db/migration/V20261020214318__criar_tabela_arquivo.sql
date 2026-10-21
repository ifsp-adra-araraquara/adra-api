-- Metadados dos arquivos guardados no storage (o binário NUNCA vai pro banco).
-- Dono polimórfico (entidade_tipo + entidade_id), sem FK: o ArquivoService valida o dono.
-- "categoria" fica sem CHECK de propósito: categoria nova = novo valor no enum Java, sem migration.

CREATE TABLE IF NOT EXISTS adra.arquivo (
    arquivo_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entidade_tipo  varchar(40)  NOT NULL,
    entidade_id    bigint       NOT NULL,
    categoria      varchar(40)  NOT NULL,
    provedor       varchar(20)  NOT NULL,
    chave          varchar(500) NOT NULL,
    nome_original  varchar(255),
    mime_type      varchar(100) NOT NULL,
    tamanho_bytes  bigint       NOT NULL,
    hash_sha256    char(64)     NOT NULL,
    enviado_por_id bigint REFERENCES adra.usuario (usuario_id),
    criado_em      timestamp with time zone DEFAULT now() NOT NULL,
    excluido_em    timestamp with time zone,
    CONSTRAINT uq_arquivo_chave UNIQUE (chave),
    CONSTRAINT ck_arquivo_entidade_tipo CHECK (entidade_tipo IN ('ASSISTIDO', 'RESPONSAVEL', 'USUARIO')),
    CONSTRAINT ck_arquivo_provedor CHECK (provedor IN ('SUPABASE', 'R2')),
    CONSTRAINT ck_arquivo_tamanho CHECK (tamanho_bytes >= 0)
);

-- Listagem por dono (só os ativos) e checagem de duplicata por hash.
CREATE INDEX IF NOT EXISTS idx_arquivo_dono
    ON adra.arquivo (entidade_tipo, entidade_id, categoria)
    WHERE excluido_em IS NULL;

-- No máximo uma foto de perfil ativa por dono (CategoriaArquivo.FOTO_PERFIL.unicoPorDono).
CREATE UNIQUE INDEX IF NOT EXISTS uq_arquivo_foto_perfil_ativa
    ON adra.arquivo (entidade_tipo, entidade_id)
    WHERE categoria = 'FOTO_PERFIL' AND excluido_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_arquivo_enviado_por ON adra.arquivo (enviado_por_id);

ALTER TABLE adra.arquivo ENABLE ROW LEVEL SECURITY;
