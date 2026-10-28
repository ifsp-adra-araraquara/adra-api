-- FT-05 (Painel do Coordenador): índices para as agregações do painel.
-- Todas as consultas recortam a aula por período e juntam por turma; sem
-- estes índices o Postgres varre a tabela inteira a cada abertura do painel.

-- Frequência do mês/tendência e chamadas pendentes: filtra por data e status.
CREATE INDEX IF NOT EXISTS aula_data_status_idx ON adra.aula (data_aula, status_aula);

-- Junção vínculo -> aulas da turma (faltas seguidas, frequência por turma).
CREATE INDEX IF NOT EXISTS aula_turma_data_idx ON adra.aula (turma_id, data_aula);

-- Presença é esparsa (só F/FJ); o LEFT JOIN do painel busca por (aula, assistido).
CREATE INDEX IF NOT EXISTS presenca_aula_assistido_idx ON adra.presenca (aula_id, assistido_id);

-- Registros disciplinares do mês e do mês anterior.
CREATE INDEX IF NOT EXISTS registro_disciplinar_data_idx
    ON adra.registro_disciplinar (data_registro) WHERE arquivado = false;
