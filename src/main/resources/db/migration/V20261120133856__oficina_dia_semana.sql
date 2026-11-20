-- Dias da semana em que a oficina acontece, marcados pelo Coordenador.
-- Oficinas já cadastradas ficam sem dia até alguém editar.
CREATE TABLE IF NOT EXISTS adra.oficina_dia_semana (
    oficina_id  bigint      NOT NULL REFERENCES adra.oficina (oficina_id) ON DELETE CASCADE,
    dia_semana  varchar(20) NOT NULL,
    PRIMARY KEY (oficina_id, dia_semana)
);

CREATE INDEX IF NOT EXISTS oficina_dia_semana_dia_idx
    ON adra.oficina_dia_semana (dia_semana);

ALTER TABLE adra.oficina_dia_semana ENABLE ROW LEVEL SECURITY;
