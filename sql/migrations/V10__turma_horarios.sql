-- ============================================================
-- MIGRACAO V10: Adicionar horarios e dias da semana na tabela turma
-- ============================================================

SET search_path TO adra, public;

-- Adicionar colunas de horario na tabela turma
ALTER TABLE adra.turma
    ADD COLUMN IF NOT EXISTS horario_inicio time;

ALTER TABLE adra.turma
    ADD COLUMN IF NOT EXISTS horario_fim time;

-- Criar tabela para armazenar dias da semana da turma
CREATE TABLE IF NOT EXISTS adra.turma_dia_semana (
    turma_id bigint NOT NULL,
    dia_semana varchar(20) NOT NULL,
    
    CONSTRAINT turma_dia_semana_pkey PRIMARY KEY (turma_id, dia_semana),
    CONSTRAINT turma_dia_semana_turma_fkey 
        FOREIGN KEY (turma_id) REFERENCES adra.turma(turma_id) ON DELETE CASCADE
);

-- Criar indice para busca por dia da semana
CREATE INDEX IF NOT EXISTS turma_dia_semana_dia_idx 
    ON adra.turma_dia_semana(dia_semana);
