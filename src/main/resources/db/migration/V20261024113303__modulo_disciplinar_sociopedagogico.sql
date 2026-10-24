-- FT-04: o Sociopedagógico também registra ocorrências, então passa a ver o
-- módulo Disciplinar no menu (o Coordenador já tinha). Oficineiro continua sem.
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT np.nivel_permissao_id, m.modulo_id, 6, false
  FROM adra.nivel_permissao np, adra.modulo m
 WHERE np.nome = 'SOCIOPEDAGOGICO'
   AND m.codigo = 'DISCIPLINAR'
ON CONFLICT DO NOTHING;
