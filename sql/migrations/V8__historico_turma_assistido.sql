-- ============================================================
-- MIGRACAO V8: Historico de turma do assistido (US-A04)
-- ============================================================
-- Ate aqui, assistido.turma_id era sobrescrito direto a cada
-- edicao (UPDATE destrutivo) - a troca de turma nao deixava
-- rastro da turma anterior. Esta tabela registra cada periodo em
-- que o assistido esteve numa turma: data_fim NULL = vinculo
-- aberto (turma atual); ao trocar de turma, o service fecha o
-- registro aberto (seta data_fim) e abre um novo.

SET search_path TO adra, public;

CREATE TABLE IF NOT EXISTS adra.assistido_turma_historico (
    historico_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assistido_id  bigint NOT NULL REFERENCES adra.assistido(assistido_id) ON DELETE CASCADE,
    turma_id      bigint REFERENCES adra.turma(turma_id) ON DELETE SET NULL,
    data_inicio   timestamp NOT NULL DEFAULT now(),
    data_fim      timestamp,
    criado_em     timestamp NOT NULL DEFAULT now()
);

-- Acelera a busca do vinculo aberto de um assistido (feita a cada
-- troca de turma, para saber o que fechar).
CREATE INDEX IF NOT EXISTS assistido_turma_historico_aberto_idx
    ON adra.assistido_turma_historico (assistido_id)
    WHERE data_fim IS NULL;
