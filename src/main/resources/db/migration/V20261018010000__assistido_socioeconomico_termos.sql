-- Socioeconômico da família e termos/autorizações do assistido,
-- tabela de parâmetros (limite de renda familiar) e programas de transferência de renda.

-- ============================================================
-- PARÂMETROS DO SISTEMA
-- ============================================================
CREATE TABLE IF NOT EXISTS adra.parametro_sistema (
    chave          varchar(60) PRIMARY KEY,
    valor_numerico numeric(12, 2),
    descricao      text,
    atualizado_em  timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE adra.parametro_sistema ENABLE ROW LEVEL SECURITY;

-- Valor NULL = regra ainda não definida pela ADRA (ninguém é barrado).
-- Para ativar: UPDATE adra.parametro_sistema SET valor_numerico = 3000.00 WHERE chave = 'RENDA_FAMILIAR_LIMITE';
INSERT INTO adra.parametro_sistema (chave, valor_numerico, descricao)
VALUES ('RENDA_FAMILIAR_LIMITE', NULL,
        'Renda familiar mensal maxima (R$) para a familia participar do programa. NULL = sem limite definido.')
ON CONFLICT (chave) DO NOTHING;

-- ============================================================
-- ASSISTIDO: socioeconômico e termos
-- ============================================================
ALTER TABLE adra.assistido
    ADD COLUMN IF NOT EXISTS renda_familiar                numeric(12, 2),
    ADD COLUMN IF NOT EXISTS recebe_programa_transferencia boolean,
    ADD COLUMN IF NOT EXISTS programa_transferencia_outro  varchar(120),
    ADD COLUMN IF NOT EXISTS nis                           varchar(20),
    ADD COLUMN IF NOT EXISTS aceite_lgpd                   boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS aceite_lgpd_em                timestamp with time zone,
    ADD COLUMN IF NOT EXISTS autoriza_uso_imagem           boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS autoriza_eventos_passeios     boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS pode_ir_embora_sozinho        boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS ciente_frequencia_obrigatoria boolean DEFAULT false NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assistido_renda_familiar') THEN
        ALTER TABLE adra.assistido ADD CONSTRAINT ck_assistido_renda_familiar CHECK (renda_familiar >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assistido_nis') THEN
        ALTER TABLE adra.assistido ADD CONSTRAINT ck_assistido_nis CHECK (nis ~ '^\d{11}$');
    END IF;
END $$;

-- ============================================================
-- PROGRAMAS DE TRANSFERÊNCIA DE RENDA (N por assistido)
-- ============================================================
CREATE TABLE IF NOT EXISTS adra.assistido_programa_transferencia (
    assistido_id bigint      NOT NULL REFERENCES adra.assistido (assistido_id) ON DELETE CASCADE,
    programa     varchar(30) NOT NULL,
    CONSTRAINT assistido_programa_transferencia_pkey PRIMARY KEY (assistido_id, programa),
    CONSTRAINT ck_assistido_programa_transferencia CHECK (programa IN
        ('BPC', 'BOLSA_FAMILIA', 'PETI', 'BOLSA_ESCOLA', 'AUXILIO_GAS', 'ALUGUEL_SOCIAL', 'OUTRO'))
);

ALTER TABLE adra.assistido_programa_transferencia ENABLE ROW LEVEL SECURITY;

COMMENT ON COLUMN adra.assistido.renda_familiar IS 'Renda mensal da familia (R$) - comparada com parametro_sistema.RENDA_FAMILIAR_LIMITE no cadastro';
COMMENT ON COLUMN adra.assistido.nis IS 'NIS / CadUnico (11 digitos)';
COMMENT ON COLUMN adra.assistido.aceite_lgpd_em IS 'Data/hora em que o aceite LGPD foi registrado';
