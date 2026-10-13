-- ============================================================
-- MIGRACAO V2: card "Cadastrar Usuario"
-- Idempotente (IF NOT EXISTS / ON CONFLICT DO NOTHING) - seguro mesmo se
-- ja tiver sido rodado manualmente no Supabase antes do Flyway entrar.
-- ============================================================

SET search_path TO adra, public;

-- ------------------------------------------------------------
-- 1) Novo enum para a especialidade do PROFISSIONAL_SAUDE
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'especialidade_saude') THEN
        CREATE TYPE adra.especialidade_saude AS ENUM ('NEUROLOGIA', 'PSICOPEDAGOGIA', 'PSICOLOGIA');
    END IF;
END $$;

-- ------------------------------------------------------------
-- 2) Coluna especialidade em usuario (nullable - so' se aplica
--    quando nivel_permissao = PROFISSIONAL_SAUDE; a regra "so'
--    preenche quando for esse nivel" e' validada na aplicacao,
--    porque Postgres nao permite CHECK constraint que consulte
--    outra tabela).
-- ------------------------------------------------------------
ALTER TABLE adra.usuario
    ADD COLUMN IF NOT EXISTS especialidade adra.especialidade_saude;

-- ------------------------------------------------------------
-- 3) Seed dos 6 niveis de permissao (idempotente via ON CONFLICT
--    na coluna nome, que ja' e' UNIQUE na tabela original).
-- ------------------------------------------------------------
INSERT INTO adra.nivel_permissao (nome, descricao) VALUES
    ('ADMINISTRADOR',     'Acesso total ao sistema, unico perfil que cadastra usuarios'),
    ('COORDENADOR',       'Acesso amplo, incluindo controle disciplinar'),
    ('OFICINEIRO',        'Acesso somente leitura'),
    ('SOCIOPEDAGOGICO',   'Equipe sociopedagogica'),
    ('PROFISSIONAL_SAUDE','Profissional de saude com login individual por especialidade'),
    ('FINANCEIRO',        'Exportacao de dados e acompanhamento de projetos')
ON CONFLICT (nome) DO NOTHING;
