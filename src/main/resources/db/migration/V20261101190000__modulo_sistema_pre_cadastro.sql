-- O pré-cadastro audita no módulo PRE_CADASTRO, que faltava no enum do banco.
ALTER TYPE adra.modulo_sistema ADD VALUE IF NOT EXISTS 'PRE_CADASTRO';
