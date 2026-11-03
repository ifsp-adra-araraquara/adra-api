-- Semanário: plano da semana que o oficineiro envia para cada grupo atendido
-- pela oficina, um por semana. Substitui a aula. Base de teste: os materiais
-- de aula saem junto com a tabela aula.

CREATE TABLE IF NOT EXISTS adra.semanario (
    semanario_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oficina_id           bigint NOT NULL REFERENCES adra.oficina(oficina_id) ON DELETE RESTRICT,
    turma_id             bigint NOT NULL REFERENCES adra.turma(turma_id) ON DELETE RESTRICT,
    semana_inicio        date   NOT NULL,
    conteudo_previsto    text   NOT NULL,
    objetivos            text,
    recursos_necessarios text,
    enviado_por_id       bigint NOT NULL REFERENCES adra.usuario(usuario_id),
    criado_em            timestamp NOT NULL DEFAULT now(),
    atualizado_em        timestamp NOT NULL DEFAULT now(),
    CONSTRAINT ck_semanario_semana_comeca_segunda CHECK (EXTRACT(ISODOW FROM semana_inicio) = 1),
    CONSTRAINT uq_semanario_oficina_turma_semana UNIQUE (oficina_id, turma_id, semana_inicio)
);
CREATE INDEX IF NOT EXISTS semanario_turma_idx ON adra.semanario (turma_id);
CREATE INDEX IF NOT EXISTS semanario_enviado_por_idx ON adra.semanario (enviado_por_id);
DROP TRIGGER IF EXISTS semanario_atualizado_em_trigger ON adra.semanario;
CREATE TRIGGER semanario_atualizado_em_trigger
    BEFORE UPDATE ON adra.semanario
    FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();
ALTER TABLE adra.semanario ENABLE ROW LEVEL SECURITY;

DELETE FROM adra.arquivo WHERE entidade_tipo = 'AULA';
ALTER TABLE adra.arquivo DROP CONSTRAINT IF EXISTS ck_arquivo_entidade_tipo;
ALTER TABLE adra.arquivo
    ADD CONSTRAINT ck_arquivo_entidade_tipo
    CHECK (entidade_tipo IN ('ASSISTIDO', 'RESPONSAVEL', 'USUARIO', 'SEMANARIO', 'PRE_CADASTRO'));

DROP TABLE IF EXISTS adra.aula;
