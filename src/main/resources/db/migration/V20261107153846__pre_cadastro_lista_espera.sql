-- Lista de espera: inscrição correta sem vaga aguarda a decisão da equipe.
ALTER TABLE adra.pre_cadastro DROP CONSTRAINT IF EXISTS ck_pre_cadastro_status;
ALTER TABLE adra.pre_cadastro
    ADD CONSTRAINT ck_pre_cadastro_status
    CHECK (status IN ('PENDENTE', 'LISTA_ESPERA', 'CONVERTIDO', 'DESCARTADO'));
