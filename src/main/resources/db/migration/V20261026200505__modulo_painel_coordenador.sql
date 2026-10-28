-- FT-05: "Painel" passa a ser a tela inicial do Coordenador e o primeiro
-- item do menu. Os outros perfis continuam entrando pelo Calendário.

INSERT INTO adra.modulo (codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em)
SELECT 'PAINEL', 'Painel', '/painel', true, now(), now()
 WHERE NOT EXISTS (SELECT 1 FROM adra.modulo WHERE codigo = 'PAINEL');

-- Só um módulo padrão por perfil: o Coordenador tinha Dashboard e Aulas marcados.
UPDATE adra.nivel_permissao_modulo npm
   SET eh_padrao = false
  FROM adra.nivel_permissao np
 WHERE np.nivel_permissao_id = npm.nivel_permissao_id
   AND np.nome = 'COORDENADOR';

INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT np.nivel_permissao_id, m.modulo_id, 0, true
  FROM adra.nivel_permissao np, adra.modulo m
 WHERE np.nome = 'COORDENADOR'
   AND m.codigo = 'PAINEL'
ON CONFLICT (nivel_permissao_id, modulo_id) DO UPDATE SET ordem = 0, eh_padrao = true;
