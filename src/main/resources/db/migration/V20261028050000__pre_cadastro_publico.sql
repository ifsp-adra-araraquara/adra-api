-- Pré-cadastro público: link sem login que a família de um novo assistido
-- ou pretendente preenche. Nunca grava direto em adra.assistido — é uma
-- tabela de rascunho isolada; a equipe revisa e só manualmente confirma o
-- cadastro de verdade (onde CPF passa a ser exigido, igual já é hoje).
CREATE TABLE IF NOT EXISTS adra.pre_cadastro (
    pre_cadastro_id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome_completo         varchar(180) NOT NULL,
    data_nascimento       date,
    responsavel_nome      varchar(180) NOT NULL,
    responsavel_telefone  varchar(20),
    endereco              varchar(255),
    observacao            text,
    status                varchar(20) NOT NULL DEFAULT 'PENDENTE',
    revisado_por_id       bigint REFERENCES adra.usuario (usuario_id),
    revisado_em           timestamp with time zone,
    ip_origem             varchar(64),
    criado_em             timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_pre_cadastro_status CHECK (status IN ('PENDENTE', 'CONVERTIDO', 'DESCARTADO'))
);

CREATE INDEX IF NOT EXISTS idx_pre_cadastro_status ON adra.pre_cadastro (status, criado_em DESC);

ALTER TABLE adra.pre_cadastro ENABLE ROW LEVEL SECURITY;

INSERT INTO adra.modulo (codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em)
SELECT 'PRE_CADASTRO', 'Pré-cadastros', '/pre-cadastros', true, now(), now()
 WHERE NOT EXISTS (SELECT 1 FROM adra.modulo WHERE codigo = 'PRE_CADASTRO');

INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT np.nivel_permissao_id, m.modulo_id, 7, false
  FROM adra.nivel_permissao np, adra.modulo m
 WHERE np.nome IN ('COORDENADOR', 'SOCIOPEDAGOGICO')
   AND m.codigo = 'PRE_CADASTRO'
ON CONFLICT DO NOTHING;
