-- Chamada (CA-65): turma_aluno, presenca e falta_justificada.
-- Consolida sql/legacy/V12 e V13, aplicadas a mão em alguns bancos depois do baseline.
-- Idempotente; não apaga presenca antiga com dados.

DO $$
BEGIN
    IF to_regtype('adra.status_presenca') IS NULL THEN
        CREATE TYPE adra.status_presenca AS ENUM ('PRESENTE', 'FALTA', 'FALTA_JUSTIFICADA');
    END IF;
    IF to_regtype('adra.motivo_falta') IS NULL THEN
        CREATE TYPE adra.motivo_falta AS ENUM ('SAUDE', 'VIAGEM', 'TRABALHO', 'MOTIVO_FAMILIAR', 'OUTRO');
    END IF;
END $$;

-- presenca do baseline está no formato antigo: vazia é removida, com dados vira presenca_legado.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'adra' AND table_name = 'presenca'
                 AND column_name = 'horario_registro') THEN
        DROP TABLE IF EXISTS adra.falta_justificada;
        IF EXISTS (SELECT 1 FROM adra.presenca) THEN
            ALTER TABLE adra.presenca RENAME TO presenca_legado;
            ALTER INDEX IF EXISTS adra.presenca_pkey RENAME TO presenca_legado_pkey;
            ALTER INDEX IF EXISTS adra.presenca_aula_id_assistido_id_key RENAME TO presenca_legado_aula_id_assistido_id_key;
            ALTER SEQUENCE IF EXISTS adra.presenca_presenca_id_seq RENAME TO presenca_legado_presenca_id_seq;
        ELSE
            DROP TABLE adra.presenca;
        END IF;
    END IF;
END $$;

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
CREATE INDEX IF NOT EXISTS turma_aluno_turma_idx ON adra.turma_aluno (turma_id);
CREATE INDEX IF NOT EXISTS turma_aluno_assistido_idx ON adra.turma_aluno (assistido_id);
CREATE INDEX IF NOT EXISTS turma_aluno_data_idx ON adra.turma_aluno (data_entrada, data_saida);
DROP TRIGGER IF EXISTS turma_aluno_atualizado_em_trigger ON adra.turma_aluno;
CREATE TRIGGER turma_aluno_atualizado_em_trigger
    BEFORE UPDATE ON adra.turma_aluno
    FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();
ALTER TABLE adra.turma_aluno ENABLE ROW LEVEL SECURITY;

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
CREATE INDEX IF NOT EXISTS presenca_aula_idx ON adra.presenca (aula_id);
CREATE INDEX IF NOT EXISTS presenca_assistido_idx ON adra.presenca (assistido_id);
DROP TRIGGER IF EXISTS presenca_atualizado_em_trigger ON adra.presenca;
CREATE TRIGGER presenca_atualizado_em_trigger
    BEFORE UPDATE ON adra.presenca
    FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();
ALTER TABLE adra.presenca ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS adra.falta_justificada (
    falta_justificada_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    presenca_id          bigint NOT NULL UNIQUE REFERENCES adra.presenca(presenca_id) ON DELETE CASCADE,
    motivo_falta         adra.motivo_falta NOT NULL,
    observacao           text,
    criado_em            timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_falta_justificada_outro
        CHECK (motivo_falta <> 'OUTRO' OR (observacao IS NOT NULL AND btrim(observacao) <> ''))
);
CREATE INDEX IF NOT EXISTS falta_justificada_presenca_idx ON adra.falta_justificada (presenca_id);
ALTER TABLE adra.falta_justificada ENABLE ROW LEVEL SECURITY;

-- Quem já tem assistido.turma_id ganha o vínculo aberto em turma_aluno.
INSERT INTO adra.turma_aluno (turma_id, assistido_id, data_entrada, status)
SELECT a.turma_id, a.assistido_id, COALESCE(a.data_entrada, CURRENT_DATE), a.status
FROM adra.assistido a
WHERE a.turma_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM adra.turma_aluno ta
                  WHERE ta.turma_id = a.turma_id
                    AND ta.assistido_id = a.assistido_id
                    AND ta.data_saida IS NULL);
