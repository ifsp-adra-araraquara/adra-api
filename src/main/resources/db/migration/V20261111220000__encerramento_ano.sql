-- Encerramento do ano letivo (feito à mão pelo Coordenador, depois do prazo
-- de rematrícula). Uma linha por ano encerrado: registro de quem encerrou,
-- quando e o resumo — e a trava que impede abrir matrícula num ano fechado.

CREATE TABLE IF NOT EXISTS adra.encerramento_ano (
    ano                 smallint PRIMARY KEY,
    encerrado_por_id    bigint   REFERENCES adra.usuario (usuario_id),
    encerrado_em        timestamp with time zone NOT NULL DEFAULT now(),
    total_encerradas    integer  NOT NULL,
    total_renovaram     integer  NOT NULL,
    total_nao_renovaram integer  NOT NULL,
    CONSTRAINT ck_encerramento_ano CHECK (ano BETWEEN 2000 AND 2100)
);

ALTER TABLE adra.encerramento_ano ENABLE ROW LEVEL SECURITY;
