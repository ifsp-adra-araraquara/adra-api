-- ============================================================
-- SEEDS para local e staging.
-- NUNCA usar dado real de assistido fora de producao.
-- Rodar depois de V1, V2 e V3.
-- ============================================================

SET search_path TO adra, public;

-- Os auth_uid abaixo sao ficticios. Em local eles casam com o IdP de teste;
-- em staging, substituir pelo UID real criado no Supabase Auth.
INSERT INTO adra.usuario (nivel_permissao_id, nome_completo, email, cargo_funcao, telefone, auth_uid, administrador)
SELECT np.nivel_permissao_id, v.nome, v.email, v.cargo, v.telefone, v.auth_uid::uuid, v.administrador
FROM (VALUES
    ('Ana Administradora', 'administrador@adra.com',     'Administracao',    '16999990001', 'COORDENADOR',       '11111111-1111-1111-1111-111111111111', true),
    ('Carlos Coordenador', 'coordenador@adra.com',       'Coordenacao',      '16999990002', 'COORDENADOR',       '22222222-2222-2222-2222-222222222222', false),
    ('Sonia Sociopedagoga','sociopedagogico@adra.com',   'Educadora Social', '16999990003', 'EDUCADOR_SOCIAL',   '33333333-3333-3333-3333-333333333333', false),
    ('Alice Assistente',   'assistentesocial@adra.com',  'Assistente Social','16999990004', 'ASSISTENTE_SOCIAL', '44444444-4444-4444-4444-444444444444', false)
) AS v(nome, email, cargo, telefone, perfil, auth_uid, administrador)
JOIN adra.nivel_permissao np ON np.nome = v.perfil::nome_nivel_permissao
ON CONFLICT DO NOTHING;
