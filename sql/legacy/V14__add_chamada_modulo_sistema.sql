-- ============================================================
-- MIGRACAO V14: adiciona 'CHAMADA' ao enum modulo_sistema
-- ============================================================
-- Por que essa migration existe: o V1__schema_inicial.sql do repositorio ja
-- lista 'CHAMADA' na definicao de adra.modulo_sistema, mas esse arquivo foi
-- editado DEPOIS que voce rodou o V1 no banco (as migrations aqui sao coladas
-- manualmente no SQL Editor do Supabase, nao rodadas por uma ferramenta tipo
-- Flyway que reaplicaria o V1 sozinha). Ou seja: o enum que existe de fato no
-- seu banco nao tem 'CHAMADA' — so o arquivo local foi atualizado.
--
-- Sintoma: ao salvar a chamada, as presencas sao gravadas com sucesso, mas o
-- AuditoriaService tenta logar a acao com modulo=CHAMADA e o Postgres rejeita
-- com "invalid input value for enum modulo_sistema: \"CHAMADA\"".
--
-- ADD VALUE ... IF NOT EXISTS e seguro como statement solto (fora de bloco
-- DO/transacao explicita) a partir do Postgres 12 — e o que o Supabase roda.

SET search_path TO adra, public;

ALTER TYPE adra.modulo_sistema ADD VALUE IF NOT EXISTS 'CHAMADA';
