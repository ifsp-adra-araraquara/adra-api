-- ============================================================
-- MIGRACAO V13: Recria turma_aluno/presenca/falta_justificada do zero
-- ============================================================
-- Por que essa migration existe: a V12 usa CREATE TABLE IF NOT EXISTS, mas
-- turma_aluno (e possivelmente presenca/falta_justificada) já existia no seu
-- banco local com os nomes de coluna em inglês (created_at/updated_at) de um
-- rascunho anterior (o V__presenca_turma_aluno.sql que eu tinha te passado
-- antes de padronizar pro português). Como a tabela já existia, a V12 não
-- mexeu nela — daí o erro "column ta1_0.atualizado_em does not exist": as
-- entidades Java já esperam o nome novo, mas a tabela no banco ainda tem o
-- antigo.
--
-- Como são tabelas novas (CA-65), sem dado de produção nenhum ainda, a saída
-- mais segura é dropar e recriar do zero, garantindo que o schema bate 100%
-- com as entidades atuais — em vez de tentar adivinhar/corrigir coluna por
-- coluna um estado que pode estar parcialmente misturado.
--
-- ATENÇÃO: isso apaga qualquer linha que já exista em falta_justificada,
-- presenca e turma_aluno no banco em que você rodar (local/stage). Se você
-- já tiver dados reais de chamada lançados em stage/prod, avise antes de
-- rodar isso lá — mas para o ambiente local isso é exatamente o "apagar e
-- recomeçar" que você tinha dito estar disposto a fazer.

SET search_path TO adra, public;

DROP TABLE IF EXISTS adra.falta_justificada CASCADE;
DROP TABLE IF EXISTS adra.presenca CASCADE;
DROP TABLE IF EXISTS adra.turma_aluno CASCADE;

-- --- a partir daqui é a mesma criação da V12, só que agora garantidamente
-- --- do zero, com os nomes de coluna corretos -------------------------

CREATE TABLE adra.turma_aluno (
    turma_aluno_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id        bigint NOT NULL REFERENCES adra.turma(turma_id) ON DELETE CASCADE,
    assistido_id    bigint NOT NULL REFERENCES adra.assistido(assistido_id) ON DELETE CASCADE,
    data_entrada    date NOT NULL,
    data_saida      date,
    status          adra.status_geral NOT NULL DEFAULT 'ATIVO',
    criado_em       timestamp NOT NULL DEFAULT now(),
    atualizado_em   timestamp NOT NULL DEFAULT now()
);

CREATE INDEX turma_aluno_turma_idx ON adra.turma_aluno(turma_id);
CREATE INDEX turma_aluno_assistido_idx ON adra.turma_aluno(assistido_id);
CREATE INDEX turma_aluno_data_idx ON adra.turma_aluno(data_entrada, data_saida);

CREATE TRIGGER turma_aluno_atualizado_em_trigger
    BEFORE UPDATE ON adra.turma_aluno
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();

ALTER TABLE adra.turma_aluno ENABLE ROW LEVEL SECURITY;

CREATE TABLE adra.presenca (
    presenca_id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aula_id           bigint NOT NULL REFERENCES adra.aula(aula_id) ON DELETE CASCADE,
    assistido_id      bigint NOT NULL REFERENCES adra.assistido(assistido_id) ON DELETE CASCADE,
    status_presenca   adra.status_presenca NOT NULL,
    criado_por_id     bigint NOT NULL REFERENCES adra.usuario(usuario_id),
    atualizado_por_id bigint NOT NULL REFERENCES adra.usuario(usuario_id),
    criado_em         timestamp NOT NULL DEFAULT now(),
    atualizado_em     timestamp NOT NULL DEFAULT now(),

    CONSTRAINT uq_presenca_aula_assistido UNIQUE (aula_id, assistido_id)
);

CREATE INDEX presenca_aula_idx ON adra.presenca(aula_id);
CREATE INDEX presenca_assistido_idx ON adra.presenca(assistido_id);

CREATE TRIGGER presenca_atualizado_em_trigger
    BEFORE UPDATE ON adra.presenca
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();

ALTER TABLE adra.presenca ENABLE ROW LEVEL SECURITY;

CREATE TABLE adra.falta_justificada (
    falta_justificada_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    presenca_id           bigint NOT NULL UNIQUE REFERENCES adra.presenca(presenca_id) ON DELETE CASCADE,
    motivo_falta           adra.motivo_falta NOT NULL,
    observacao              text,
    criado_em               timestamp NOT NULL DEFAULT now(),

    CONSTRAINT chk_falta_justificada_outro
        CHECK (motivo_falta <> 'OUTRO' OR (observacao IS NOT NULL AND btrim(observacao) <> ''))
);

CREATE INDEX falta_justificada_presenca_idx ON adra.falta_justificada(presenca_id);

ALTER TABLE adra.falta_justificada ENABLE ROW LEVEL SECURITY;

-- Backfill (mesmo da V12 — precisa rodar de novo já que a tabela foi recriada)
INSERT INTO adra.turma_aluno (turma_id, assistido_id, data_entrada, status)
SELECT a.turma_id, a.assistido_id, COALESCE(a.data_entrada, CURRENT_DATE), a.status
FROM adra.assistido a
WHERE a.turma_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM adra.turma_aluno ta
      WHERE ta.turma_id = a.turma_id
        AND ta.assistido_id = a.assistido_id
        AND ta.data_saida IS NULL
  );
