ALTER TABLE adra.assistido
  ADD COLUMN IF NOT EXISTS data_entrada date NOT NULL DEFAULT CURRENT_DATE,
  ADD COLUMN IF NOT EXISTS data_saida date,
  ADD COLUMN IF NOT EXISTS motivo_saida text,
  ADD COLUMN IF NOT EXISTS necessidades_especificas text,
  ADD COLUMN IF NOT EXISTS observacoes text,
  ADD COLUMN IF NOT EXISTS status varchar(30) NOT NULL DEFAULT 'ATIVO',
  ADD COLUMN IF NOT EXISTS total_ocorrencias_ativas integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS total_advertencias_ativas integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS total_suspensoes integer NOT NULL DEFAULT 0;

-- limpar os defaults que só existem pra permitir o NOT NULL em colunas já
-- populadas — o @PrePersist e a lógica da aplicação já cuidam dos valores
-- daqui pra frente:
ALTER TABLE adra.assistido
  ALTER COLUMN data_entrada DROP DEFAULT;