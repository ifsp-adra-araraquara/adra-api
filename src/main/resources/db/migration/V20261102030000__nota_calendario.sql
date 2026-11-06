-- Notas/eventos globais do calendário cadastrados pela Coordenação e visíveis para todos os perfis.
CREATE TABLE IF NOT EXISTS adra.nota_calendario (
    nota_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data_nota       date         NOT NULL,
    horario         time,
    titulo          varchar(120) NOT NULL,
    descricao       varchar(500),
    criado_por_id   bigint       REFERENCES adra.usuario (usuario_id),
    criado_em       timestamp with time zone NOT NULL DEFAULT now(),
    atualizado_em   timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_nota_calendario_data
    ON adra.nota_calendario (data_nota, horario);

DROP TRIGGER IF EXISTS nota_calendario_atualizado_em_trigger ON adra.nota_calendario;
CREATE TRIGGER nota_calendario_atualizado_em_trigger
    BEFORE UPDATE ON adra.nota_calendario
    FOR EACH ROW
    EXECUTE FUNCTION adra.set_atualizado_em();

ALTER TABLE adra.nota_calendario ENABLE ROW LEVEL SECURITY;
