-- ============================================================
-- SEED MANUAL: adiciona alunos (assistidos) a uma turma existente
-- ------------------------------------------------------------
-- Isto NAO e uma migration (nao roda automatico com o Flyway/Makefile).
-- E so um script pra rodar na mao (psql, DBeaver, SQL editor do
-- Supabase etc.) quando alguem precisar popular uma turma com alunos
-- de teste ou reais.
--
-- Passo a passo:
--   1) Rode a consulta abaixo pra achar o turma_id certo.
--   2) Troque o "1" nos VALUES pelo turma_id encontrado.
--   3) Preencha nome e data de nascimento de cada aluno (obrigatorios).
--      CPF e opcional, pode deixar NULL.
--   4) Rode o INSERT.
-- ============================================================

SET search_path TO adra, public;

-- 1) Achar o turma_id (ajuste o filtro pelo nome da turma/oficina):
SELECT t.turma_id, t.nome_turma, o.nome_oficina, t.turno
FROM adra.turma t
JOIN adra.oficina o ON o.oficina_id = t.oficina_id
WHERE t.nome_turma ILIKE '%nome da turma aqui%';

-- 2) Inserir os alunos (um por linha). Troque o "1" pelo turma_id certo.
INSERT INTO adra.assistido (nome_completo, data_nascimento, cpf, turma_id, status)
VALUES
  ('Nome Completo do Aluno 1', '2015-03-10', NULL, 1, 'ATIVO'),
  ('Nome Completo do Aluno 2', '2014-07-22', NULL, 1, 'ATIVO'),
  ('Nome Completo do Aluno 3', '2016-01-05', NULL, 1, 'ATIVO');
  -- adicione quantas linhas precisar, uma por aluno

-- 3) Conferir o resultado:
SELECT a.assistido_id, a.nome_completo, a.data_nascimento, a.status, t.nome_turma
FROM adra.assistido a
JOIN adra.turma t ON t.turma_id = a.turma_id
WHERE a.turma_id = 1
ORDER BY a.nome_completo;

-- ------------------------------------------------------------
-- OPCIONAL: o app (tela de cadastro) exige pelo menos um responsavel
-- por assistido, mas isso e uma regra da API, nao do banco -- o INSERT
-- acima funciona sem responsavel nenhum. Se quiser deixar o cadastro
-- completo (por exemplo pra depois editar pelo sistema sem erro),
-- descomente e ajuste o bloco abaixo pra cada aluno:
-- ------------------------------------------------------------

-- INSERT INTO adra.responsavel (nome_completo, telefone, email)
-- VALUES ('Nome do Responsavel', '16999990000', 'responsavel@email.com')
-- RETURNING responsavel_id;
--
-- INSERT INTO adra.assistido_responsavel
--   (assistido_id, responsavel_id, parentesco, responsavel_principal, contato_emergencia, autorizado_retirada)
-- VALUES
--   (<assistido_id do aluno>, <responsavel_id retornado acima>, 'Mae/Pai', true, true, true);
