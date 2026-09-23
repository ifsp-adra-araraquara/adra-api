-- ============================================================
-- MIGRACAO V11: Status Remarcada e Tabela de Excecoes de Calendario (CA-63)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS adra;
SET search_path TO adra, public;

-- 1. Tratar o tipo enum status_aula:
-- Se ja existir em adra ou public, adiciona REMARCADA; se nao existir, cria em adra.
DO $$
DECLARE
    v_schema text;
BEGIN
    SELECT n.nspname INTO v_schema
    FROM pg_type t
    JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE t.typname = 'status_aula'
    LIMIT 1;

    IF v_schema IS NOT NULL THEN
        EXECUTE format('ALTER TYPE %I.status_aula ADD VALUE IF NOT EXISTS ''REMARCADA''', v_schema);
    ELSE
        CREATE TYPE adra.status_aula AS ENUM ('PLANEJADA', 'REALIZADA', 'CANCELADA', 'REMARCADA');
    END IF;
END $$;

-- 2. Garantir a funcao de atualizacao automatica de timestamp
CREATE OR REPLACE FUNCTION adra.set_atualizado_em()
RETURNS trigger AS $$
BEGIN
  NEW.atualizado_em = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3. Criar tabela global de excecoes de calendario (feriados/recessos)
CREATE TABLE IF NOT EXISTS adra.excecao_calendario (
    excecao_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data_excecao    date NOT NULL UNIQUE,
    tipo            varchar(30) NOT NULL,
    descricao       varchar(150),
    criado_em       timestamp NOT NULL DEFAULT now(),
    atualizado_em   timestamp NOT NULL DEFAULT now()
);

-- Indice para consultas por data ou intervalo de datas
CREATE INDEX IF NOT EXISTS excecao_calendario_data_idx 
    ON adra.excecao_calendario(data_excecao);

-- Trigger para atualizado_em
DROP TRIGGER IF EXISTS excecao_calendario_atualizado_em_trigger ON adra.excecao_calendario;
CREATE TRIGGER excecao_calendario_atualizado_em_trigger
    BEFORE UPDATE ON adra.excecao_calendario
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();

-- Habilitar Row Level Security
ALTER TABLE adra.excecao_calendario ENABLE ROW LEVEL SECURITY;
