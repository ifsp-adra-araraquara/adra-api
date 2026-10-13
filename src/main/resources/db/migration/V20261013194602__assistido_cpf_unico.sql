-- CPF do assistido continua opcional, mas único quando informado.
UPDATE adra.assistido SET cpf = NULL WHERE btrim(cpf) = '';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'uq_assistido_cpf'
                     AND conrelid = 'adra.assistido'::regclass) THEN
        ALTER TABLE adra.assistido ADD CONSTRAINT uq_assistido_cpf UNIQUE (cpf);
    END IF;
END $$;
