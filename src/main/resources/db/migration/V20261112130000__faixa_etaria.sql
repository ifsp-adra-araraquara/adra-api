-- Faixas etárias cadastráveis pela coordenação; o grupo aponta para uma delas.
-- Sobreposição é permitida; o mesmo intervalo não se repete.
CREATE TABLE IF NOT EXISTS adra.faixa_etaria (
    faixa_etaria_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idade_minima    smallint NOT NULL,
    idade_maxima    smallint NOT NULL,
    ativo           boolean  NOT NULL DEFAULT true,
    criado_em       timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_faixa_etaria_intervalo CHECK (idade_minima >= 0 AND idade_maxima >= idade_minima),
    CONSTRAINT uq_faixa_etaria_intervalo UNIQUE (idade_minima, idade_maxima)
);

ALTER TABLE adra.faixa_etaria ENABLE ROW LEVEL SECURITY;

INSERT INTO adra.faixa_etaria (idade_minima, idade_maxima) VALUES (6, 10), (11, 13), (14, 17);

ALTER TABLE adra.turma ADD COLUMN faixa_etaria_id bigint REFERENCES adra.faixa_etaria (faixa_etaria_id);
CREATE INDEX IF NOT EXISTS turma_faixa_etaria_idx ON adra.turma (faixa_etaria_id);

-- Texto livre antigo -> faixas novas do cliente: 6–9 → 6–10, 10–13 → 11–13, 14–17 → 14–17. O resto fica sem faixa.
UPDATE adra.turma t
   SET faixa_etaria_id = f.faixa_etaria_id
  FROM (VALUES (6, 9, 6, 10), (10, 13, 11, 13), (14, 17, 14, 17)) AS m (de_min, de_max, para_min, para_max)
  JOIN adra.faixa_etaria f ON f.idade_minima = m.para_min AND f.idade_maxima = m.para_max
 WHERE (regexp_match(t.faixa_etaria, '(\d+)\D+(\d+)'))[1]::int = m.de_min
   AND (regexp_match(t.faixa_etaria, '(\d+)\D+(\d+)'))[2]::int = m.de_max;

ALTER TABLE adra.turma DROP COLUMN faixa_etaria;
