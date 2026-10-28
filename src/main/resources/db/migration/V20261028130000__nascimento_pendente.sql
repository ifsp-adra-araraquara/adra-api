-- Importação inicial de assistidos reais a partir das planilhas de presença
-- da ADRA: parte deles não tem data de nascimento confiável na fonte
-- (coluna "NIVER" só com dia/mês). Libera o campo pra ficar pendente até a
-- equipe confirmar com a família, em vez de bloquear o cadastro inteiro.
ALTER TABLE adra.assistido ALTER COLUMN data_nascimento DROP NOT NULL;
