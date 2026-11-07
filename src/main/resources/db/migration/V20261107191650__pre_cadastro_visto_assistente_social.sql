-- Marca a inscrição aberta pela Assistente Social, para a equipe saber o que ainda não foi visto.
ALTER TABLE adra.pre_cadastro ADD COLUMN visto_assistente_social boolean NOT NULL DEFAULT false;
