-- ============================================================
-- MIGRACAO V3: Supabase Auth como IdP
-- Idempotente (IF NOT EXISTS / IF EXISTS).
-- ============================================================

SET search_path TO adra, public;

-- auth_uid nullable: registros anteriores a esta migracao nao tem
-- identidade no Supabase. Vira NOT NULL apos o backfill.
ALTER TABLE adra.usuario
    ADD COLUMN IF NOT EXISTS auth_uid uuid;

CREATE UNIQUE INDEX IF NOT EXISTS usuario_auth_uid_uk
    ON adra.usuario(auth_uid);

-- Excecao consciente a regra de migracao aditiva: a coluna e NOT NULL e
-- esta em uso, mas a senha passou a ser responsabilidade do Supabase Auth.
ALTER TABLE adra.usuario
    DROP COLUMN IF EXISTS senha_hash;

-- Sem policy: nenhum role comum le ou escreve. O dono da tabela continua
-- passando; anon/authenticated (PostgREST) ficam sem acesso.
-- Sem FORCE de proposito -- FORCE aplicaria RLS ao dono e bloquearia o backend.
DO $$
DECLARE t record;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'adra'
    LOOP
        EXECUTE format('ALTER TABLE adra.%I ENABLE ROW LEVEL SECURITY', t.tablename);
    END LOOP;
END $$;
