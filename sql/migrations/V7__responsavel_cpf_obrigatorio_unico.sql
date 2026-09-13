-- ============================================================
-- MIGRACAO V7: Modulo Responsaveis (US "cadastrar/editar responsavel")
-- ============================================================

SET search_path TO adra, public;

DO $$
DECLARE
  v_modulo_id bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM adra.modulo WHERE codigo = 'RESPONSAVEIS') THEN
    INSERT INTO adra.modulo (codigo, nome_exibicao, rota, ativo)
    VALUES ('RESPONSAVEIS', 'Responsáveis', '/responsaveis', true)
    RETURNING modulo_id INTO v_modulo_id;
  ELSE
    SELECT modulo_id INTO v_modulo_id FROM adra.modulo WHERE codigo = 'RESPONSAVEIS';
  END IF;

  -- COORDENADOR (nivel_permissao_id = 2)
  IF NOT EXISTS (
    SELECT 1 FROM adra.nivel_permissao_modulo
    WHERE nivel_permissao_id = 2 AND modulo_id = v_modulo_id
  ) THEN
    INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
    VALUES (2, v_modulo_id, 8, false);
  END IF;

  -- SOCIOPEDAGOGICO (nivel_permissao_id = 4)
  IF NOT EXISTS (
    SELECT 1 FROM adra.nivel_permissao_modulo
    WHERE nivel_permissao_id = 4 AND modulo_id = v_modulo_id
  ) THEN
    INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
    VALUES (4, v_modulo_id, 3, false);
  END IF;
END $$;

-- ============================================================
-- MIGRACAO V7: CPF obrigatorio e unico em Responsavel
-- ============================================================

SET search_path TO adra, public;

ALTER TABLE adra.responsavel
    ALTER COLUMN cpf SET NOT NULL;

ALTER TABLE adra.responsavel
    ADD CONSTRAINT uq_responsavel_cpf UNIQUE (cpf);