-- FT-19: o grupo (turma) não tem mais oficina nem oficineiro. A oficina guarda o
-- oficineiro e diz, em oficina_turma, quais grupos atende. Base de teste: os
-- vínculos antigos turma -> oficina são descartados e refeitos na tela.

CREATE TABLE IF NOT EXISTS adra.oficina_turma (
    oficina_turma_id bigint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oficina_id       bigint  NOT NULL REFERENCES adra.oficina(oficina_id) ON DELETE RESTRICT,
    turma_id         bigint  NOT NULL REFERENCES adra.turma(turma_id) ON DELETE RESTRICT,
    ativo            boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_oficina_turma UNIQUE (oficina_id, turma_id)
);
CREATE INDEX IF NOT EXISTS oficina_turma_turma_idx ON adra.oficina_turma (turma_id);
ALTER TABLE adra.oficina_turma ENABLE ROW LEVEL SECURITY;

-- A aula deixa de olhar o oficineiro da turma: as antigas sem oficineiro ficam com o da turma.
UPDATE adra.aula a
   SET oficineiro_id = t.oficineiro_id
  FROM adra.turma t
 WHERE t.turma_id = a.turma_id
   AND a.oficineiro_id IS NULL
   AND t.oficineiro_id IS NOT NULL;

DROP TABLE IF EXISTS adra.turma_oficineiro_historico;

ALTER TABLE adra.turma
    DROP COLUMN IF EXISTS oficina_id,
    DROP COLUMN IF EXISTS oficineiro_id,
    DROP COLUMN IF EXISTS oficineiro_responsavel,
    DROP COLUMN IF EXISTS horario_inicio,
    DROP COLUMN IF EXISTS horario_fim;
