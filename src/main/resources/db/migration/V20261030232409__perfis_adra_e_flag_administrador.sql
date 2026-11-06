-- FT-07 e FT-10: os perfis passam a ser os da ADRA, e Administrador vira
-- uma flag do usuário em vez de um perfil.

ALTER TABLE adra.usuario ADD COLUMN administrador boolean NOT NULL DEFAULT false;

UPDATE adra.usuario
   SET administrador = true,
       nivel_permissao_id = (SELECT nivel_permissao_id FROM adra.nivel_permissao WHERE nome = 'COORDENADOR')
 WHERE nivel_permissao_id = (SELECT nivel_permissao_id FROM adra.nivel_permissao WHERE nome = 'ADMINISTRADOR');

-- Se ainda houver usuário com Profissional de Saúde ou Financeiro, a FK barra
-- o DELETE e a migration falha: decidir o perfil dele antes de rodar.
DELETE FROM adra.nivel_permissao WHERE nome IN ('ADMINISTRADOR', 'PROFISSIONAL_SAUDE', 'FINANCEIRO');

ALTER TABLE adra.usuario DROP COLUMN especialidade;
DROP TYPE adra.especialidade_saude;

-- O Postgres não remove valor de enum: recria o tipo só com os perfis atuais.
ALTER TYPE adra.nome_nivel_permissao RENAME VALUE 'SOCIOPEDAGOGICO' TO 'EDUCADOR_SOCIAL';
ALTER TYPE adra.nome_nivel_permissao RENAME TO nome_nivel_permissao_antigo;
CREATE TYPE adra.nome_nivel_permissao AS ENUM ('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO');
ALTER TABLE adra.nivel_permissao
    ALTER COLUMN nome TYPE adra.nome_nivel_permissao USING nome::text::adra.nome_nivel_permissao;
DROP TYPE adra.nome_nivel_permissao_antigo;

UPDATE adra.nivel_permissao
   SET descricao = 'Chamada no dia, ocorrências e acompanhamento dos atendidos.'
 WHERE nome = 'EDUCADOR_SOCIAL';

INSERT INTO adra.nivel_permissao (nome, descricao)
VALUES ('ASSISTENTE_SOCIAL', 'Cadastro, socioeconômico e aprovação de pré-cadastros.');

-- A Assistente Social vê o mesmo menu do Educador Social; os pré-cadastros
-- passam a ser só dela e do Coordenador.
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT nova.nivel_permissao_id, npm.modulo_id, npm.ordem, npm.eh_padrao
  FROM adra.nivel_permissao_modulo npm
  JOIN adra.nivel_permissao educador ON educador.nivel_permissao_id = npm.nivel_permissao_id
 CROSS JOIN adra.nivel_permissao nova
 WHERE educador.nome = 'EDUCADOR_SOCIAL'
   AND nova.nome = 'ASSISTENTE_SOCIAL';

DELETE FROM adra.nivel_permissao_modulo npm
 USING adra.nivel_permissao np, adra.modulo m
 WHERE np.nivel_permissao_id = npm.nivel_permissao_id
   AND m.modulo_id = npm.modulo_id
   AND np.nome = 'EDUCADOR_SOCIAL'
   AND m.codigo = 'PRE_CADASTRO';

-- Materiais sai do menu até o FT-20; o Oficineiro passa a entrar por Turmas.
UPDATE adra.nivel_permissao_modulo npm
   SET eh_padrao = false
  FROM adra.nivel_permissao np
 WHERE np.nivel_permissao_id = npm.nivel_permissao_id
   AND np.nome = 'OFICINEIRO';

UPDATE adra.nivel_permissao_modulo npm
   SET ordem = 0, eh_padrao = true
  FROM adra.nivel_permissao np, adra.modulo m
 WHERE np.nivel_permissao_id = npm.nivel_permissao_id
   AND m.modulo_id = npm.modulo_id
   AND np.nome = 'OFICINEIRO'
   AND m.codigo = 'TURMAS';
