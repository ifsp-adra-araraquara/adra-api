-- FT-04: sistema disciplinar (Livro de Ocorrências -> Advertência Escrita -> Suspensão, mais a trilha do uniforme).
-- Regras nos cartazes "Disciplina ADRA" e no comunicado de uso do uniforme:
--   3ª ocorrência  -> advertência escrita (pais assinam)
--   3ª advertência -> suspensão de 3 dias
--   3ª suspensão   -> perde o próximo passeio
--   3ª de uniforme -> só retorna acompanhado do responsável
-- A progressão é feita no DisciplinarService, não por trigger.

-- Motivos cadastráveis pela coordenação; o nível vem do motivo.
CREATE TABLE IF NOT EXISTS adra.motivo_disciplinar (
    motivo_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome       varchar(150) NOT NULL,
    nivel      varchar(20)  NOT NULL,
    ativo      boolean      NOT NULL DEFAULT true,
    criado_em  timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_motivo_disciplinar_nivel CHECK (nivel IN ('OCORRENCIA', 'ADVERTENCIA', 'SUSPENSAO', 'UNIFORME')),
    CONSTRAINT uq_motivo_disciplinar_nome_nivel UNIQUE (nome, nivel)
);

CREATE TABLE IF NOT EXISTS adra.registro_disciplinar (
    registro_id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assistido_id                bigint  NOT NULL REFERENCES adra.assistido (assistido_id),
    -- NULL quando o registro foi gerado por progressão (ex.: advertência pela 3ª ocorrência)
    motivo_id                   bigint  REFERENCES adra.motivo_disciplinar (motivo_id),
    nivel                       varchar(20) NOT NULL,
    origem                      varchar(20) NOT NULL DEFAULT 'MANUAL',
    -- registro que disparou este, quando origem = PROGRESSAO
    registro_origem_id          bigint  REFERENCES adra.registro_disciplinar (registro_id),
    -- posição deste registro entre os ativos do mesmo nível no momento do registro (1ª, 2ª, 3ª...)
    sequencia                   integer NOT NULL,
    data_registro               date    NOT NULL,
    descricao                   text    NOT NULL,
    aula_id                     bigint  REFERENCES adra.aula (aula_id),
    suspensao_inicio            date,
    suspensao_fim               date,
    -- documento para os pais assinarem (advertência, suspensão, comunicado de uniforme); NULL na ocorrência simples
    documento_status            varchar(30),
    documento_arquivo_id        bigint  REFERENCES adra.arquivo (arquivo_id),
    -- o que este registro disparou ao completar 3 do mesmo nível
    consequencia                varchar(30),
    consequencia_resolvida_em   date,
    consequencia_observacao     text,
    consequencia_resolvida_por_id bigint REFERENCES adra.usuario (usuario_id),
    arquivado                   boolean NOT NULL DEFAULT false,
    arquivado_motivo            text,
    arquivado_em                timestamp with time zone,
    arquivado_por_id            bigint  REFERENCES adra.usuario (usuario_id),
    registrado_por_id           bigint  NOT NULL REFERENCES adra.usuario (usuario_id),
    criado_em                   timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_registro_disciplinar_nivel CHECK (nivel IN ('OCORRENCIA', 'ADVERTENCIA', 'SUSPENSAO', 'UNIFORME')),
    CONSTRAINT ck_registro_disciplinar_origem CHECK (origem IN ('MANUAL', 'PROGRESSAO')),
    CONSTRAINT ck_registro_disciplinar_documento CHECK (documento_status IS NULL OR documento_status IN ('PENDENTE_ASSINATURA', 'DEVOLVIDO_ASSINADO')),
    CONSTRAINT ck_registro_disciplinar_consequencia CHECK (consequencia IS NULL OR consequencia IN
        ('ADVERTENCIA_GERADA', 'SUSPENSAO_GERADA', 'PERDE_PROXIMO_PASSEIO', 'RETORNO_COM_RESPONSAVEL')),
    CONSTRAINT ck_registro_disciplinar_suspensao CHECK (
        (nivel = 'SUSPENSAO' AND suspensao_inicio IS NOT NULL AND suspensao_fim IS NOT NULL AND suspensao_fim >= suspensao_inicio)
        OR (nivel <> 'SUSPENSAO' AND suspensao_inicio IS NULL AND suspensao_fim IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_registro_disciplinar_assistido
    ON adra.registro_disciplinar (assistido_id, nivel) WHERE arquivado = false;

ALTER TABLE adra.motivo_disciplinar ENABLE ROW LEVEL SECURITY;
ALTER TABLE adra.registro_disciplinar ENABLE ROW LEVEL SECURITY;

-- Motivos dos cartazes "Disciplina ADRA" e do comunicado de uniforme.
INSERT INTO adra.motivo_disciplinar (nome, nivel) VALUES
    ('Falar palavrão frequentemente', 'OCORRENCIA'),
    ('Desrespeitar o professor e educadores', 'OCORRENCIA'),
    ('Brigas e discussões', 'OCORRENCIA'),
    ('Brigar com o colega', 'OCORRENCIA'),
    ('Danos ao patrimônio', 'OCORRENCIA'),
    ('Xingamentos direcionados à família ou educadores', 'ADVERTENCIA'),
    ('Agressão', 'ADVERTENCIA'),
    ('Quebra intencional de patrimônio', 'ADVERTENCIA'),
    ('Piadas de mau gosto', 'ADVERTENCIA'),
    ('Ameaças verbais', 'ADVERTENCIA'),
    ('Situações com risco à integridade física ou emocional', 'SUSPENSAO'),
    ('Ameaças sérias e explícitas', 'SUSPENSAO'),
    ('Bullying', 'SUSPENSAO'),
    ('Preconceito', 'SUSPENSAO'),
    ('Desrespeito às normas institucionais', 'SUSPENSAO'),
    ('Danos graves ao patrimônio', 'SUSPENSAO'),
    ('Uso do uniforme', 'UNIFORME')
ON CONFLICT ON CONSTRAINT uq_motivo_disciplinar_nome_nivel DO NOTHING;
