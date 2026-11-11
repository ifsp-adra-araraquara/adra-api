-- Matrícula por ano letivo.
--
-- A ficha (adra.assistido) guarda quem a criança é; o que é refeito todo ano
-- (termos/autorizações, grupo, formulário enviado e anexos) passa a pertencer
-- à matrícula do ano. Quem não renova não ganha matrícula no ano seguinte.
--
-- Guarda de dados: 5 anos por matrícula (descarte em PR próprio). Por isso o
-- vínculo com grupo e a matrícula caem em cascata quando a matrícula sai.

CREATE TABLE IF NOT EXISTS adra.matricula (
    matricula_id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assistido_id                  bigint      NOT NULL REFERENCES adra.assistido (assistido_id) ON DELETE CASCADE,
    ano                           smallint    NOT NULL,
    status                        varchar(20) NOT NULL DEFAULT 'ATIVA',
    -- Formulário que gerou a matrícula (o envio da família naquele ano).
    pre_cadastro_id               bigint      REFERENCES adra.pre_cadastro (pre_cadastro_id) ON DELETE SET NULL,
    aceite_lgpd                   boolean     NOT NULL DEFAULT false,
    aceite_lgpd_em                timestamp with time zone,
    autoriza_uso_imagem           boolean     NOT NULL DEFAULT false,
    autoriza_eventos_passeios     boolean     NOT NULL DEFAULT false,
    pode_ir_embora_sozinho        boolean     NOT NULL DEFAULT false,
    ciente_frequencia_obrigatoria boolean     NOT NULL DEFAULT false,
    criado_em                     timestamp with time zone NOT NULL DEFAULT now(),
    atualizado_em                 timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_matricula_assistido_ano UNIQUE (assistido_id, ano),
    CONSTRAINT ck_matricula_ano CHECK (ano BETWEEN 2000 AND 2100),
    CONSTRAINT ck_matricula_status CHECK (status IN ('ATIVA', 'ENCERRADA', 'DESLIGADA'))
);

-- Lista do ano, encerramento do ano e descarte filtram por (ano, status).
CREATE INDEX IF NOT EXISTS idx_matricula_ano_status ON adra.matricula (ano, status);
CREATE INDEX IF NOT EXISTS idx_matricula_pre_cadastro ON adra.matricula (pre_cadastro_id)
    WHERE pre_cadastro_id IS NOT NULL;

ALTER TABLE adra.matricula ENABLE ROW LEVEL SECURITY;

-- Matrícula mais recente do atendido: de onde a ficha, a listagem e a chamada
-- leem os termos, sem varrer o histórico.
ALTER TABLE adra.assistido
    ADD COLUMN IF NOT EXISTS matricula_atual_id bigint
        REFERENCES adra.matricula (matricula_id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_assistido_matricula_atual ON adra.assistido (matricula_atual_id);

-- Grupo de cada ano: o vínculo pertence à matrícula daquele ano.
ALTER TABLE adra.turma_aluno
    ADD COLUMN IF NOT EXISTS matricula_id bigint
        REFERENCES adra.matricula (matricula_id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_turma_aluno_matricula ON adra.turma_aluno (matricula_id);

-- Carga: os cadastros atuais ficam como 2026 (decisão da ADRA), com os termos
-- que estão hoje na ficha. As colunas de termos da ficha deixam de ser usadas
-- e saem num PR posterior, depois deste em produção.
INSERT INTO adra.matricula (assistido_id, ano, status, aceite_lgpd, aceite_lgpd_em, autoriza_uso_imagem,
                            autoriza_eventos_passeios, pode_ir_embora_sozinho, ciente_frequencia_obrigatoria,
                            criado_em, atualizado_em)
SELECT a.assistido_id,
       2026,
       CASE WHEN a.status::text = 'ATIVO' THEN 'ATIVA' ELSE 'DESLIGADA' END,
       a.aceite_lgpd,
       a.aceite_lgpd_em,
       a.autoriza_uso_imagem,
       a.autoriza_eventos_passeios,
       a.pode_ir_embora_sozinho,
       a.ciente_frequencia_obrigatoria,
       a.criado_em,
       now()
  FROM adra.assistido a
ON CONFLICT (assistido_id, ano) DO NOTHING;

-- Quem veio do pré-cadastro: liga a matrícula ao envio (o mais recente aprovado).
UPDATE adra.matricula m
   SET pre_cadastro_id = p.pre_cadastro_id
  FROM (SELECT DISTINCT ON (assistido_id) assistido_id, pre_cadastro_id
          FROM adra.pre_cadastro
         WHERE status = 'CONVERTIDO' AND assistido_id IS NOT NULL
         ORDER BY assistido_id, criado_em DESC) p
 WHERE m.assistido_id = p.assistido_id
   AND m.ano = 2026
   AND m.pre_cadastro_id IS NULL;

UPDATE adra.assistido a
   SET matricula_atual_id = m.matricula_id
  FROM adra.matricula m
 WHERE m.assistido_id = a.assistido_id
   AND m.ano = 2026
   AND a.matricula_atual_id IS NULL;

UPDATE adra.turma_aluno ta
   SET matricula_id = m.matricula_id
  FROM adra.matricula m
 WHERE m.assistido_id = ta.assistido_id
   AND m.ano = 2026
   AND ta.matricula_id IS NULL;
