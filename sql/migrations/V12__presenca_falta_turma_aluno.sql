-- ============================================================
-- MIGRACAO V12: Chamada (CA-65) - turma_aluno, presenca, falta_justificada
-- ============================================================
-- Cria as tabelas que o CA-65 (lançar presença) já usa no código Java, mas
-- que nunca tiveram migration própria. Sem isso nada roda contra um banco
-- real (dev/stage/prod) — presenca/falta_justificada/turma_aluno não existem
-- em lugar nenhum hoje.
--
-- Convenções seguidas (mesmas de V8/V11): tipos enum nativos do Postgres
-- (não varchar), nomes de coluna em português (criado_em/atualizado_em,
-- timestamp sem timezone), trigger adra.set_atualizado_em() e o padrão
-- defensivo "cria o tipo só se não existir em nenhum schema" pra não
-- duplicar os enums status_presenca/status_geral que já existem soltos no
-- dump local (schema_adra.sql) sem migration que os tenha criado.

CREATE SCHEMA IF NOT EXISTS adra;
SET search_path TO adra, public;

-- 1) Tipos enum nativos -----------------------------------------------------

DO $$
DECLARE
    v_schema text;
BEGIN
    SELECT n.nspname INTO v_schema
    FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE t.typname = 'status_geral'
    LIMIT 1;

    IF v_schema IS NULL THEN
        CREATE TYPE adra.status_geral AS ENUM ('ATIVO', 'INATIVO');
    END IF;
END $$;

DO $$
DECLARE
    v_schema text;
BEGIN
    SELECT n.nspname INTO v_schema
    FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE t.typname = 'status_presenca'
    LIMIT 1;

    IF v_schema IS NULL THEN
        CREATE TYPE adra.status_presenca AS ENUM ('PRESENTE', 'FALTA', 'FALTA_JUSTIFICADA');
    END IF;
END $$;

DO $$
DECLARE
    v_schema text;
BEGIN
    SELECT n.nspname INTO v_schema
    FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE t.typname = 'motivo_falta'
    LIMIT 1;

    IF v_schema IS NULL THEN
        -- Valores provisórios (ver comentário no MotivoFalta.java) — pendente
        -- de confirmação com a coordenação, per a própria task do CA-65.
        CREATE TYPE adra.motivo_falta AS ENUM ('SAUDE', 'VIAGEM', 'TRABALHO', 'MOTIVO_FAMILIAR', 'OUTRO');
    END IF;
END $$;

-- 2) Função de trigger (idempotente — já existe desde V11, repetida aqui por
-- segurança caso esta migration rode isolada num banco que não tenha V11).
CREATE OR REPLACE FUNCTION adra.set_atualizado_em()
RETURNS trigger AS $$
BEGIN
  NEW.atualizado_em = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3) turma_aluno: histórico de vínculo do assistido com a turma -------------
-- (sem UNIQUE em turma_id+assistido_id de propósito: é histórico, um mesmo
-- par pode se repetir ao longo do tempo com dataEntrada/dataSaida diferentes)

CREATE TABLE IF NOT EXISTS adra.turma_aluno (
    turma_aluno_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id        bigint NOT NULL REFERENCES adra.turma(turma_id) ON DELETE CASCADE,
    assistido_id    bigint NOT NULL REFERENCES adra.assistido(assistido_id) ON DELETE CASCADE,
    data_entrada    date NOT NULL,
    data_saida      date,
    status          adra.status_geral NOT NULL DEFAULT 'ATIVO',
    criado_em       timestamp NOT NULL DEFAULT now(),
    atualizado_em   timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS turma_aluno_turma_idx ON adra.turma_aluno(turma_id);
CREATE INDEX IF NOT EXISTS turma_aluno_assistido_idx ON adra.turma_aluno(assistido_id);
CREATE INDEX IF NOT EXISTS turma_aluno_data_idx ON adra.turma_aluno(data_entrada, data_saida);

DROP TRIGGER IF EXISTS turma_aluno_atualizado_em_trigger ON adra.turma_aluno;
CREATE TRIGGER turma_aluno_atualizado_em_trigger
    BEFORE UPDATE ON adra.turma_aluno
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();

ALTER TABLE adra.turma_aluno ENABLE ROW LEVEL SECURITY;

-- 4) presenca: uma linha por (aula, assistido) — modelo esparso (CA-65) ------
-- Só existe linha para FALTA / FALTA_JUSTIFICADA; PRESENTE é inferido pela
-- ausência de linha (roster da turma_aluno menos quem tem falta registrada).

CREATE TABLE IF NOT EXISTS adra.presenca (
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

CREATE INDEX IF NOT EXISTS presenca_aula_idx ON adra.presenca(aula_id);
CREATE INDEX IF NOT EXISTS presenca_assistido_idx ON adra.presenca(assistido_id);

DROP TRIGGER IF EXISTS presenca_atualizado_em_trigger ON adra.presenca;
CREATE TRIGGER presenca_atualizado_em_trigger
    BEFORE UPDATE ON adra.presenca
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();

ALTER TABLE adra.presenca ENABLE ROW LEVEL SECURITY;

-- 5) falta_justificada: satélite 1:1 opcional de presenca (CA-65.2) ---------
-- Só existe uma linha aqui quando presenca.status_presenca = FALTA_JUSTIFICADA
-- — criada/removida pelo service (PresencaMapper.sincronizarFaltaJustificada),
-- não por cascade do JPA.

CREATE TABLE IF NOT EXISTS adra.falta_justificada (
    falta_justificada_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    presenca_id           bigint NOT NULL UNIQUE REFERENCES adra.presenca(presenca_id) ON DELETE CASCADE,
    motivo_falta           adra.motivo_falta NOT NULL,
    observacao              text,
    criado_em               timestamp NOT NULL DEFAULT now(),

    CONSTRAINT chk_falta_justificada_outro
        CHECK (motivo_falta <> 'OUTRO' OR (observacao IS NOT NULL AND btrim(observacao) <> ''))
);

CREATE INDEX IF NOT EXISTS falta_justificada_presenca_idx ON adra.falta_justificada(presenca_id);

ALTER TABLE adra.falta_justificada ENABLE ROW LEVEL SECURITY;

-- 6) Backfill: assistidos já vinculados via assistido.turma_id --------------
-- (fluxo de cadastro anterior a esta migration) ganham um vínculo histórico
-- correspondente em turma_aluno. Sem isso o roster da chamada (CA-65) e a
-- validação "assistido pertence à turma da aula" (CA-65.1) ficam vazios pra
-- todo mundo que já estava matriculado antes desta migration.
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
