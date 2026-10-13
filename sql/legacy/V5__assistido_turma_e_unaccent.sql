-- ============================================================
-- MIGRACAO V5: Associacao Assistido -> Turma e extensao unaccent
-- ============================================================

SET search_path TO adra, public;

-- 1) Extensao unaccent para busca tolerante a acentos
CREATE EXTENSION IF NOT EXISTS unaccent;

-- 2) Coluna turma_id na tabela assistido
ALTER TABLE adra.assistido
    ADD COLUMN IF NOT EXISTS turma_id bigint REFERENCES adra.turma(turma_id) ON DELETE SET NULL;

-- 3) Indice na chave estrangeira
CREATE INDEX IF NOT EXISTS assistido_turma_idx ON adra.assistido(turma_id);
