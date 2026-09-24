-- ============================================================
-- MIGRACAO V8: Modulo Aulas (US-XX)
-- ============================================================

SET search_path TO adra, public;

CREATE TABLE IF NOT EXISTS adra.aula (
    aula_id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id                bigint NOT NULL,
    titulo                  varchar(150),
    descricao               text,
    data_aula               date NOT NULL,
    horario_inicio          time,
    horario_fim             time,
    conteudo_previsto       text,
    conteudo_ministrado     text,
    objetivos               text,
    recursos_necessarios    text,
    status_aula             adra.status_aula NOT NULL DEFAULT 'PLANEJADA',
    observacoes             text,
    criado_em               timestamp NOT NULL DEFAULT now(),
    atualizado_em           timestamp NOT NULL DEFAULT now(),
    
    CONSTRAINT aula_turma_data_uk UNIQUE (turma_id, data_aula)
);

-- Foreign key para turma
ALTER TABLE adra.aula
    ADD CONSTRAINT aula_turma_fkey 
    FOREIGN KEY (turma_id) REFERENCES adra.turma(turma_id) ON DELETE CASCADE;

-- Índices para performance
CREATE INDEX IF NOT EXISTS aula_turma_idx ON adra.aula(turma_id);
CREATE INDEX IF NOT EXISTS aula_data_idx ON adra.aula(data_aula);
CREATE INDEX IF NOT EXISTS aula_status_idx ON adra.aula(status_aula);

-- Trigger para atualizar atualizado_em automaticamente
CREATE TRIGGER aula_atualizado_em_trigger
    BEFORE UPDATE ON adra.aula
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();
