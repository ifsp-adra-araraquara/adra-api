-- Período de matrícula e rematrícula.
--
-- O Coordenador abre o período de um ano (ex.: 2027); enquanto ele estiver
-- aberto, o link público grava as inscrições nesse ano, separadas da lista
-- atual. Sem período aberto, a inscrição é do ano corrente (como hoje).

CREATE TABLE IF NOT EXISTS adra.periodo_matricula (
    ano            smallint PRIMARY KEY,
    inicio         date     NOT NULL,
    -- null = aberto até o Coordenador fechar
    fim            date,
    alterado_por_id bigint  REFERENCES adra.usuario (usuario_id),
    criado_em      timestamp with time zone NOT NULL DEFAULT now(),
    atualizado_em  timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_periodo_matricula_ano CHECK (ano BETWEEN 2000 AND 2100),
    CONSTRAINT ck_periodo_matricula_datas CHECK (fim IS NULL OR fim >= inicio)
);

ALTER TABLE adra.periodo_matricula ENABLE ROW LEVEL SECURITY;

-- Ano a que a inscrição se refere. As existentes ficam no ano em que chegaram.
ALTER TABLE adra.pre_cadastro ADD COLUMN IF NOT EXISTS ano_referencia smallint;
UPDATE adra.pre_cadastro
   SET ano_referencia = EXTRACT(YEAR FROM criado_em AT TIME ZONE 'America/Sao_Paulo')
 WHERE ano_referencia IS NULL;
ALTER TABLE adra.pre_cadastro ALTER COLUMN ano_referencia SET NOT NULL;

-- Lista de pré-cadastros filtra por ano e status, em ordem de chegada.
CREATE INDEX IF NOT EXISTS idx_pre_cadastro_ano_status
    ON adra.pre_cadastro (ano_referencia, status, criado_em);

-- Documentos de cada ano pertencem à matrícula daquele ano (a foto de perfil
-- continua só da ficha). Sem cascata de propósito: o descarte remove o
-- binário do Storage antes de apagar a linha — o banco recusa apagar a
-- matrícula enquanto houver arquivo dela, então nada fica órfão no bucket.
ALTER TABLE adra.arquivo
    ADD COLUMN IF NOT EXISTS matricula_id bigint REFERENCES adra.matricula (matricula_id);
CREATE INDEX IF NOT EXISTS idx_arquivo_matricula ON adra.arquivo (matricula_id)
    WHERE matricula_id IS NOT NULL;

-- Documentos do atendido já enviados são da matrícula de 2026.
UPDATE adra.arquivo a
   SET matricula_id = m.matricula_id
  FROM adra.matricula m
 WHERE a.entidade_tipo = 'ASSISTIDO'
   AND a.entidade_id = m.assistido_id
   AND m.ano = 2026
   AND a.categoria IN ('DOCUMENTO_IDENTIDADE', 'DECLARACAO_RESIDENCIA')
   AND a.matricula_id IS NULL;
