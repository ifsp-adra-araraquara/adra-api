-- FT-22: ao chegar a 3 do mesmo nível o sistema só recomenda o próximo nível;
-- o Coordenador gera (origem RECOMENDACAO) ou dispensa.
ALTER TABLE adra.registro_disciplinar DROP CONSTRAINT IF EXISTS ck_registro_disciplinar_origem;
UPDATE adra.registro_disciplinar SET origem = 'RECOMENDACAO' WHERE origem = 'PROGRESSAO';
ALTER TABLE adra.registro_disciplinar
    ADD CONSTRAINT ck_registro_disciplinar_origem
    CHECK (origem IN ('MANUAL', 'RECOMENDACAO'));

ALTER TABLE adra.registro_disciplinar DROP CONSTRAINT IF EXISTS ck_registro_disciplinar_consequencia;
UPDATE adra.registro_disciplinar SET consequencia = 'RECOMENDA_ADVERTENCIA' WHERE consequencia = 'ADVERTENCIA_GERADA';
UPDATE adra.registro_disciplinar SET consequencia = 'RECOMENDA_SUSPENSAO' WHERE consequencia = 'SUSPENSAO_GERADA';
ALTER TABLE adra.registro_disciplinar
    ADD CONSTRAINT ck_registro_disciplinar_consequencia
    CHECK (consequencia IS NULL OR consequencia IN
        ('RECOMENDA_ADVERTENCIA', 'RECOMENDA_SUSPENSAO', 'PERDE_PROXIMO_PASSEIO', 'RETORNO_COM_RESPONSAVEL'));
