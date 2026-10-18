-- Cada aula guarda quem deu a aula. Antes o nome vinha sempre da turma, então
-- trocar o oficineiro da turma "reescrevia" o histórico das aulas antigas.

ALTER TABLE adra.aula
    ADD COLUMN IF NOT EXISTS oficineiro_id bigint REFERENCES adra.usuario (usuario_id);

CREATE INDEX IF NOT EXISTS idx_aula_oficineiro ON adra.aula (oficineiro_id);

-- Preenche as aulas existentes: o oficineiro do período do histórico que cobre
-- a data da aula; sem período correspondente, o oficineiro atual da turma.
UPDATE adra.aula a
   SET oficineiro_id = COALESCE(
           (SELECT h.oficineiro_id
              FROM adra.turma_oficineiro_historico h
             WHERE h.turma_id = a.turma_id
               AND a.data_aula >= h.data_inicio
               AND (h.data_fim IS NULL OR a.data_aula <= h.data_fim)
             ORDER BY h.data_inicio DESC, h.historico_id DESC
             LIMIT 1),
           t.oficineiro_id)
  FROM adra.turma t
 WHERE t.turma_id = a.turma_id
   AND a.oficineiro_id IS NULL;
