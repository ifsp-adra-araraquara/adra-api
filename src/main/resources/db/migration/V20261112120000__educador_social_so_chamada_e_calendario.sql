-- Pedido da ADRA (11/11/2026): o Educador Social usa só o menu Pedagógico,
-- Chamada e Calendário. Os demais menus saem; a API também deixa de responder
-- a ele nesses módulos (ver @PreAuthorize dos controllers).

DELETE FROM adra.nivel_permissao_modulo npm
 USING adra.nivel_permissao np, adra.modulo m
 WHERE np.nivel_permissao_id = npm.nivel_permissao_id
   AND m.modulo_id = npm.modulo_id
   AND np.nome = 'EDUCADOR_SOCIAL'
   AND m.codigo NOT IN ('CHAMADA', 'DASHBOARD');

-- Garante os dois (caso algum ambiente não tenha) e entra direto na Chamada.
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT np.nivel_permissao_id, m.modulo_id,
       CASE m.codigo WHEN 'CHAMADA' THEN 1 ELSE 2 END,
       m.codigo = 'CHAMADA'
  FROM adra.nivel_permissao np
 CROSS JOIN adra.modulo m
 WHERE np.nome = 'EDUCADOR_SOCIAL'
   AND m.codigo IN ('CHAMADA', 'DASHBOARD')
ON CONFLICT (nivel_permissao_id, modulo_id)
DO UPDATE SET ordem = EXCLUDED.ordem, eh_padrao = EXCLUDED.eh_padrao;
