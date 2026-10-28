-- Material de aula: arquivo com dono AULA (entidade_id = aula_id).
ALTER TABLE adra.arquivo DROP CONSTRAINT IF EXISTS ck_arquivo_entidade_tipo;
ALTER TABLE adra.arquivo
    ADD CONSTRAINT ck_arquivo_entidade_tipo
    CHECK (entidade_tipo IN ('ASSISTIDO', 'RESPONSAVEL', 'USUARIO', 'AULA'));
