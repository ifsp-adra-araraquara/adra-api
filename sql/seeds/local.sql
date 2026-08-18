-- ============================================================
-- SEEDS para local e staging.
-- NUNCA usar dado real de assistido fora de producao.
-- Rodar depois de V1, V2 e V3.
-- ============================================================

SET search_path TO adra, public;

-- Os auth_uid abaixo sao ficticios. Em local eles casam com o IdP de teste;
-- em staging, substituir pelo UID real criado no Supabase Auth.
INSERT INTO adra.usuario (nivel_permissao_id, nome_completo, email, cargo_funcao, telefone, auth_uid)
SELECT np.nivel_permissao_id, v.nome, v.email, v.cargo, v.telefone, v.auth_uid::uuid
FROM (VALUES
    ('Ana Administradora', 'admin@adra.local',  'Administracao',  '16999990001', 'ADMINISTRADOR',   '11111111-1111-1111-1111-111111111111'),
    ('Carlos Coordenador', 'coord@adra.local',  'Coordenacao',    '16999990002', 'COORDENADOR',     '22222222-2222-2222-2222-222222222222'),
    ('Sonia Sociopedagoga','socio@adra.local',  'Sociopedagogico','16999990003', 'SOCIOPEDAGOGICO', '33333333-3333-3333-3333-333333333333')
) AS v(nome, email, cargo, telefone, perfil, auth_uid)
JOIN adra.nivel_permissao np ON np.nome = v.perfil::nome_nivel_permissao
ON CONFLICT DO NOTHING;
