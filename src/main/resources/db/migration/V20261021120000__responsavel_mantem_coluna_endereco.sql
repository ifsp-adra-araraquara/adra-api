-- A versão anterior da API ainda lê "endereco"; manter o nome permite rollback.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'adra' AND table_name = 'responsavel' AND column_name = 'endereco_legado')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'adra' AND table_name = 'responsavel' AND column_name = 'endereco') THEN
        ALTER TABLE adra.responsavel RENAME COLUMN endereco_legado TO endereco;
    END IF;
END $$;
