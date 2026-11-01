-- FT-11: o link público passa a receber o formulário de inscrição inteiro.
-- Identificadores em colunas; o resto do formulário (que ainda vai mudar com
-- a ADRA) em "dados". responsavel_nome/telefone guardam a mãe, ou o
-- responsável legal quando não há mãe.
ALTER TABLE adra.pre_cadastro
    ADD COLUMN IF NOT EXISTS cpf               varchar(14),
    ADD COLUMN IF NOT EXISTS sexo              varchar(20),
    ADD COLUMN IF NOT EXISTS email             varchar(180),
    ADD COLUMN IF NOT EXISTS dados             jsonb NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS motivo_reprovacao text,
    ADD COLUMN IF NOT EXISTS assistido_id      bigint REFERENCES adra.assistido (assistido_id);

CREATE INDEX IF NOT EXISTS idx_pre_cadastro_cpf ON adra.pre_cadastro (cpf);
