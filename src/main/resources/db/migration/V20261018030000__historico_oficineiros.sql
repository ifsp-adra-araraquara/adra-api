-- Histórico de oficineiros responsáveis por oficina e por turma (quem deu aula e até quando).
-- data_fim NULL = responsável atual. Trocar o oficineiro fecha o período e abre outro.

CREATE TABLE IF NOT EXISTS adra.oficina_oficineiro_historico (
    historico_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oficina_id    bigint NOT NULL REFERENCES adra.oficina (oficina_id),
    oficineiro_id bigint NOT NULL REFERENCES adra.usuario (usuario_id),
    data_inicio   date   NOT NULL,
    data_fim      date,
    CONSTRAINT ck_oficina_oficineiro_historico_periodo CHECK (data_fim IS NULL OR data_fim >= data_inicio)
);

CREATE TABLE IF NOT EXISTS adra.turma_oficineiro_historico (
    historico_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id      integer NOT NULL REFERENCES adra.turma (turma_id),
    oficineiro_id bigint NOT NULL REFERENCES adra.usuario (usuario_id),
    data_inicio   date   NOT NULL,
    data_fim      date,
    CONSTRAINT ck_turma_oficineiro_historico_periodo CHECK (data_fim IS NULL OR data_fim >= data_inicio)
);

-- No máximo um período aberto por oficina/turma.
CREATE UNIQUE INDEX IF NOT EXISTS uq_oficina_oficineiro_historico_aberto
    ON adra.oficina_oficineiro_historico (oficina_id) WHERE data_fim IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_turma_oficineiro_historico_aberto
    ON adra.turma_oficineiro_historico (turma_id) WHERE data_fim IS NULL;

ALTER TABLE adra.oficina_oficineiro_historico ENABLE ROW LEVEL SECURITY;
ALTER TABLE adra.turma_oficineiro_historico ENABLE ROW LEVEL SECURITY;

-- Ponto de partida: o oficineiro atual de cada oficina/turma, desde a criação.
INSERT INTO adra.oficina_oficineiro_historico (oficina_id, oficineiro_id, data_inicio)
SELECT o.oficina_id, o.oficineiro_id, o.criado_em::date
  FROM adra.oficina o
 WHERE o.oficineiro_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM adra.oficina_oficineiro_historico h WHERE h.oficina_id = o.oficina_id);

INSERT INTO adra.turma_oficineiro_historico (turma_id, oficineiro_id, data_inicio)
SELECT t.turma_id, t.oficineiro_id, t.criado_em::date
  FROM adra.turma t
 WHERE t.oficineiro_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM adra.turma_oficineiro_historico h WHERE h.turma_id = t.turma_id);

-- ============================================================
-- Calendário: tipos novos de dia sem aula (coluna é varchar; só documenta).
-- FERIADO, RECESSO, FERIAS, PONTO_FACULTATIVO, EVENTO_PASSEIO, OUTRO
-- ============================================================
COMMENT ON COLUMN adra.excecao_calendario.tipo IS 'FERIADO, RECESSO, FERIAS, PONTO_FACULTATIVO, EVENTO_PASSEIO ou OUTRO';
