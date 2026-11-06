-- CPF do responsável passa a ser opcional, mas único quando informado.
ALTER TABLE adra.responsavel ALTER COLUMN cpf DROP NOT NULL;
UPDATE adra.responsavel SET cpf = NULL WHERE btrim(cpf) = '';
