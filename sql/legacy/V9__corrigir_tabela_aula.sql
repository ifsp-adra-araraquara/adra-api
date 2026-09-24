-- ============================================================
-- MIGRACAO V9: Correcao tabela aula - remover oficina_id
-- ============================================================
-- Motivo: A tabela aula foi criada com oficina_id, mas o modelo
-- Java usa apenas turma_id. A relacao com oficina e via turma.

SET search_path TO adra, public;

-- Remover coluna oficina_id se existir
ALTER TABLE adra.aula DROP COLUMN IF EXISTS oficina_id;
