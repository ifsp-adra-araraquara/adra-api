-- ============================================================
-- MIGRATION: Converter campo turno de ordinal para string
-- Motivo: Alterar @Enumerated de ORDINAL para STRING no Hibernate
-- ============================================================

-- Verificar o tipo atual da coluna
-- SELECT column_name, data_type FROM information_schema.columns 
-- WHERE table_name = 'turma' AND column_name = 'turno';

-- Converter dados: 0 -> 'MANHA', 1 -> 'TARDE', 2 -> 'INTEGRAL'
UPDATE adra.turma
SET turno = CASE 
    WHEN turno::text = '0' THEN 'MANHA'
    WHEN turno::text = '1' THEN 'TARDE'
    WHEN turno::text = '2' THEN 'INTEGRAL'
    ELSE turno::text  -- Se já for string, mantém
END
WHERE turno IS NOT NULL;

-- Garantir que a coluna seja varchar (se não for)
-- ALTER TABLE adra.turma ALTER COLUMN turno TYPE varchar(20);
