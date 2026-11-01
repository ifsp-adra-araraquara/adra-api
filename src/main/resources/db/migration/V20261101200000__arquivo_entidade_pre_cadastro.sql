-- FT-11: foto e documentos enviados pelo link público ficam com dono PRE_CADASTRO
-- até a aprovação, quando passam para o assistido e o responsável.
ALTER TABLE adra.arquivo DROP CONSTRAINT IF EXISTS ck_arquivo_entidade_tipo;
ALTER TABLE adra.arquivo
    ADD CONSTRAINT ck_arquivo_entidade_tipo
    CHECK (entidade_tipo IN ('ASSISTIDO', 'RESPONSAVEL', 'USUARIO', 'AULA', 'PRE_CADASTRO'));
