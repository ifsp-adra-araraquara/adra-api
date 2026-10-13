-- ============================================================
-- MIGRAÇÃO V16: CPF único em Assistido (continua opcional)
-- ============================================================

SET search_path TO adra, public;

UPDATE adra.assistido
  SET cpf = NULL
  WHERE btrim(cpf) = '';

ALTER TABLE adra.assistido
  ADD CONSTRAINT uq_assistido_cpf UNIQUE (cpf);
