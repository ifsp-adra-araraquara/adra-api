-- FT-13: falta justificada só por Atestado ou Suspensão (reunião de 28/10).
-- O atestado não é anexado (fica no prontuário físico); a suspensão vem do Sistema Disciplinar.
-- Base de teste: os motivos antigos (SAUDE, VIAGEM, TRABALHO, MOTIVO_FAMILIAR, OUTRO) viram ATESTADO.

ALTER TABLE adra.falta_justificada DROP CONSTRAINT IF EXISTS chk_falta_justificada_outro;

ALTER TYPE adra.motivo_falta RENAME TO motivo_falta_antigo;
CREATE TYPE adra.motivo_falta AS ENUM ('ATESTADO', 'SUSPENSAO');

ALTER TABLE adra.falta_justificada
    ALTER COLUMN motivo_falta TYPE adra.motivo_falta USING 'ATESTADO'::adra.motivo_falta;

DROP TYPE adra.motivo_falta_antigo;
