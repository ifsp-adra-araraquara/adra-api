-- ============================================================
-- MIGRACAO V7: Modulo Turma (US-XX)
-- ============================================================

SET search_path TO adra, public;

CREATE TYPE IF NOT EXISTS adra.turno AS ENUM (
    'MANHA',
    'TARDE',
    'NOITE'
);

CREATE TABLE IF NOT EXISTS adra.turma (
    turma_id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oficina_id              bigint NOT NULL,
    oficineiro_id           bigint NOT NULL,
    nome_turma              varchar(100) NOT NULL,
    turno                   adra.turno NOT NULL,
    faixa_etaria            varchar(50),
    capacidade              integer NOT NULL,
    ativo                   boolean NOT NULL DEFAULT true,
    observacoes             text,
    criado_em               timestamp NOT NULL DEFAULT now(),
    atualizado_em           timestamp NOT NULL DEFAULT now()
);

-- Foreign keys
ALTER TABLE adra.turma
    ADD CONSTRAINT turma_oficina_fkey 
    FOREIGN KEY (oficina_id) REFERENCES adra.oficina(oficina_id) ON DELETE CASCADE;

ALTER TABLE adra.turma
    ADD CONSTRAINT turma_oficineiro_fkey 
    FOREIGN KEY (oficineiro_id) REFERENCES adra.usuario(usuario_id) ON DELETE RESTRICT;

-- Índices para performance
CREATE INDEX IF NOT EXISTS turma_oficina_idx ON adra.turma(oficina_id);
CREATE INDEX IF NOT EXISTS turma_oficineiro_idx ON adra.turma(oficineiro_id);
CREATE INDEX IF NOT EXISTS turma_ativo_idx ON adra.turma(ativo);

-- Trigger para atualizar atualizado_em automaticamente
CREATE TRIGGER turma_atualizado_em_trigger
    BEFORE UPDATE ON adra.turma
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();
