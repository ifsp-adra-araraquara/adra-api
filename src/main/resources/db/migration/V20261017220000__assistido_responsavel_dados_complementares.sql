-- Dados complementares do cadastro de assistido e responsável:
--   assistido   -> sexo, saúde, PCD, escola e endereço
--   responsável -> escolaridade, estado civil, profissão, renda e endereço estruturado
-- Todas as colunas são opcionais (registros antigos ficam com NULL).
-- Só adiciona colunas em tabelas que já têm RLS habilitado - nada muda nas policies.

-- ============================================================
-- ASSISTIDO
-- ============================================================
ALTER TABLE adra.assistido
    ADD COLUMN IF NOT EXISTS sexo                      varchar(20),
    ADD COLUMN IF NOT EXISTS possui_problema_saude     boolean,
    ADD COLUMN IF NOT EXISTS problema_saude            text,
    ADD COLUMN IF NOT EXISTS pcd                       boolean,
    ADD COLUMN IF NOT EXISTS deficiencia               text,
    ADD COLUMN IF NOT EXISTS escola_nome               varchar(180),
    ADD COLUMN IF NOT EXISTS escola_ano_serie          varchar(40),
    ADD COLUMN IF NOT EXISTS escola_turno              varchar(20),
    ADD COLUMN IF NOT EXISTS estuda_contraturno        boolean,
    ADD COLUMN IF NOT EXISTS endereco_logradouro       varchar(180),
    ADD COLUMN IF NOT EXISTS endereco_numero           varchar(20),
    ADD COLUMN IF NOT EXISTS endereco_bairro           varchar(120),
    ADD COLUMN IF NOT EXISTS endereco_ponto_referencia varchar(255);

-- ============================================================
-- RESPONSÁVEL
-- ============================================================
ALTER TABLE adra.responsavel
    ADD COLUMN IF NOT EXISTS escolaridade              varchar(30),
    ADD COLUMN IF NOT EXISTS estado_civil              varchar(20),
    ADD COLUMN IF NOT EXISTS profissao                 varchar(120),
    ADD COLUMN IF NOT EXISTS renda                     numeric(12, 2),
    ADD COLUMN IF NOT EXISTS endereco_logradouro       varchar(180),
    ADD COLUMN IF NOT EXISTS endereco_numero           varchar(20),
    ADD COLUMN IF NOT EXISTS endereco_bairro           varchar(120),
    ADD COLUMN IF NOT EXISTS endereco_ponto_referencia varchar(255);

-- O endereço antigo era um texto livre. Ele é preservado em "endereco_legado"
-- (não é mais lido pela API) e copiado para a rua, para a equipe só ajustar
-- número/bairro. Pode ser removido numa migration futura depois de conferido.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'adra' AND table_name = 'responsavel' AND column_name = 'endereco') THEN
        UPDATE adra.responsavel
           SET endereco_logradouro = left(btrim(endereco), 180)
         WHERE endereco_logradouro IS NULL
           AND btrim(coalesce(endereco, '')) <> '';

        ALTER TABLE adra.responsavel RENAME COLUMN endereco TO endereco_legado;
    END IF;
END $$;

-- ============================================================
-- CHECKs (valores dos enums Java)
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assistido_sexo') THEN
        ALTER TABLE adra.assistido ADD CONSTRAINT ck_assistido_sexo
            CHECK (sexo IN ('FEMININO', 'MASCULINO', 'OUTRO'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assistido_escola_turno') THEN
        ALTER TABLE adra.assistido ADD CONSTRAINT ck_assistido_escola_turno
            CHECK (escola_turno IN ('MANHA', 'TARDE', 'INTEGRAL'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_responsavel_escolaridade') THEN
        ALTER TABLE adra.responsavel ADD CONSTRAINT ck_responsavel_escolaridade
            CHECK (escolaridade IN ('SEM_ESCOLARIDADE', 'FUNDAMENTAL_INCOMPLETO', 'FUNDAMENTAL_COMPLETO',
                                    'MEDIO_INCOMPLETO', 'MEDIO_COMPLETO', 'SUPERIOR_INCOMPLETO',
                                    'SUPERIOR_COMPLETO', 'POS_GRADUACAO'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_responsavel_estado_civil') THEN
        ALTER TABLE adra.responsavel ADD CONSTRAINT ck_responsavel_estado_civil
            CHECK (estado_civil IN ('SOLTEIRO', 'CASADO', 'UNIAO_ESTAVEL', 'SEPARADO', 'DIVORCIADO', 'VIUVO'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_responsavel_renda') THEN
        ALTER TABLE adra.responsavel ADD CONSTRAINT ck_responsavel_renda CHECK (renda >= 0);
    END IF;
END $$;

COMMENT ON COLUMN adra.assistido.problema_saude IS 'Dado sensivel (LGPD art. 11) - so preenchido quando possui_problema_saude = true';
COMMENT ON COLUMN adra.assistido.deficiencia IS 'Dado sensivel (LGPD art. 11) - so preenchido quando pcd = true';
COMMENT ON COLUMN adra.responsavel.renda IS 'Renda mensal individual em reais';
