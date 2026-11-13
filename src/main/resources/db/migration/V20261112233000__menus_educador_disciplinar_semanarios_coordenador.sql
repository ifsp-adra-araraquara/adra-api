-- Ajuste de acesso pedido pela ADRA (12/11/2026). Só mexe na configuração do menu
-- (adra.modulo e adra.nivel_permissao_modulo); nenhum dado de atendido, chamada,
-- registro ou semanário é alterado.
--
-- 1. Educador Social volta a ver Disciplinar: quem registra ocorrência é o educador
--    (advertência em diante é do Coordenador — regra no DisciplinarService).
--    Complementa V20261112120000, que tinha deixado só Chamada e Calendário.
-- 2. Novo menu "Semanários" (todas as oficinas), só para o Coordenador.
-- 3. Assistente Social deixa de ver o menu Oficinas (as oficinas de cada grupo
--    continuam visíveis em Grupos).
-- O Oficineiro não tem mais menu vindo do banco: Grupos, Calendário e Semanário
-- ficam fixos no front (sidebar.ts), então nada muda para ele aqui.

-- 1. Educador Social: Chamada (1), Calendário (2), Disciplinar (3)
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT np.nivel_permissao_id, m.modulo_id, 3, false
  FROM adra.nivel_permissao np, adra.modulo m
 WHERE np.nome = 'EDUCADOR_SOCIAL'
   AND m.codigo = 'DISCIPLINAR'
ON CONFLICT (nivel_permissao_id, modulo_id) DO NOTHING;

-- 2. Módulo Semanários + Coordenador
INSERT INTO adra.modulo (codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em)
SELECT 'SEMANARIOS', 'Semanários', '/semanarios', true, now(), now()
 WHERE NOT EXISTS (SELECT 1 FROM adra.modulo WHERE codigo = 'SEMANARIOS');

INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT np.nivel_permissao_id, m.modulo_id,
       COALESCE((SELECT max(x.ordem) + 1 FROM adra.nivel_permissao_modulo x
                  WHERE x.nivel_permissao_id = np.nivel_permissao_id), 0),
       false
  FROM adra.nivel_permissao np, adra.modulo m
 WHERE np.nome = 'COORDENADOR'
   AND m.codigo = 'SEMANARIOS'
ON CONFLICT (nivel_permissao_id, modulo_id) DO NOTHING;

-- 3. Assistente Social sem o menu Oficinas (remove só o vínculo perfil ↔ menu)
DELETE FROM adra.nivel_permissao_modulo npm
 USING adra.nivel_permissao np, adra.modulo m
 WHERE np.nivel_permissao_id = npm.nivel_permissao_id
   AND m.modulo_id = npm.modulo_id
   AND np.nome = 'ASSISTENTE_SOCIAL'
   AND m.codigo = 'OFICINAS';
