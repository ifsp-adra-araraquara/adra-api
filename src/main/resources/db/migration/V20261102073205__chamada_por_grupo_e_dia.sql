-- FT-12: uma chamada por grupo (turma) por dia, sem depender de aula.
-- presenca.aula_id e registro_disciplinar.aula_id saem no FT-15, junto com a tabela aula.

CREATE TABLE IF NOT EXISTS adra.chamada (
    chamada_id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id          bigint NOT NULL REFERENCES adra.turma(turma_id) ON DELETE RESTRICT,
    data_chamada      date   NOT NULL,
    criado_por_id     bigint NOT NULL REFERENCES adra.usuario(usuario_id),
    atualizado_por_id bigint NOT NULL REFERENCES adra.usuario(usuario_id),
    criado_em         timestamp NOT NULL DEFAULT now(),
    atualizado_em     timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_chamada_turma_data UNIQUE (turma_id, data_chamada)
);
DROP TRIGGER IF EXISTS chamada_atualizado_em_trigger ON adra.chamada;
CREATE TRIGGER chamada_atualizado_em_trigger
    BEFORE UPDATE ON adra.chamada
    FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();
ALTER TABLE adra.chamada ENABLE ROW LEVEL SECURITY;

ALTER TABLE adra.presenca ALTER COLUMN aula_id DROP NOT NULL;
ALTER TABLE adra.presenca
    ADD COLUMN IF NOT EXISTS chamada_id bigint REFERENCES adra.chamada(chamada_id) ON DELETE RESTRICT;
ALTER TABLE adra.presenca DROP CONSTRAINT IF EXISTS uq_presenca_chamada_assistido;
ALTER TABLE adra.presenca
    ADD CONSTRAINT uq_presenca_chamada_assistido UNIQUE (chamada_id, assistido_id);

ALTER TABLE adra.registro_disciplinar
    ADD COLUMN IF NOT EXISTS turma_id bigint REFERENCES adra.turma(turma_id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS registro_disciplinar_turma_idx ON adra.registro_disciplinar (turma_id);

-- Um vínculo ativo por atendido (CA-FT12.6). Na base de teste, quem tiver mais de um
-- fica só com o mais recente; os outros são encerrados para o índice poder ser criado.
UPDATE adra.turma_aluno antigo
SET status = 'INATIVO', data_saida = CURRENT_DATE
WHERE antigo.status = 'ATIVO'
  AND antigo.data_saida IS NULL
  AND EXISTS (
      SELECT 1 FROM adra.turma_aluno recente
      WHERE recente.assistido_id = antigo.assistido_id
        AND recente.status = 'ATIVO'
        AND recente.data_saida IS NULL
        AND (recente.data_entrada, recente.turma_aluno_id) > (antigo.data_entrada, antigo.turma_aluno_id)
  );
CREATE UNIQUE INDEX IF NOT EXISTS uq_turma_aluno_um_ativo_por_assistido
    ON adra.turma_aluno (assistido_id)
    WHERE status = 'ATIVO' AND data_saida IS NULL;
