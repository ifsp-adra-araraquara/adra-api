-- ============================================================
-- MIGRAÇÃO V7: Módulo Responsáveis
-- US: cadastrar/editar responsável
-- ============================================================

SET search_path TO adra, public;

DO $$
DECLARE
  v_modulo_id bigint;
  v_coordenador_id bigint;
  v_sociopedagogico_id bigint;
BEGIN

  -- ==========================================================
  -- MÓDULO RESPONSÁVEIS
  -- ==========================================================

  IF NOT EXISTS (
    SELECT 1
    FROM adra.modulo
    WHERE codigo = 'RESPONSAVEIS'
  ) THEN

    INSERT INTO adra.modulo (
      codigo,
      nome_exibicao,
      rota,
      ativo
    )
    VALUES (
      'RESPONSAVEIS',
      'Responsáveis',
      '/responsaveis',
      true
    )
    RETURNING modulo_id INTO v_modulo_id;

  ELSE

    SELECT modulo_id
    INTO v_modulo_id
    FROM adra.modulo
    WHERE codigo = 'RESPONSAVEIS';

  END IF;


  -- ==========================================================
  -- BUSCA DOS NÍVEIS DE PERMISSÃO
  -- Não utilizar IDs fixos, pois eles podem variar entre
  -- ambientes/bancos.
  -- ==========================================================

  SELECT nivel_permissao_id
  INTO v_coordenador_id
  FROM adra.nivel_permissao
  WHERE nome = 'COORDENADOR';

  SELECT nivel_permissao_id
  INTO v_sociopedagogico_id
  FROM adra.nivel_permissao
  WHERE nome = 'SOCIOPEDAGOGICO';


  -- ==========================================================
  -- VALIDAÇÕES
  -- ==========================================================

  IF v_coordenador_id IS NULL THEN
    RAISE EXCEPTION 'Nível COORDENADOR não encontrado.';
  END IF;

  IF v_sociopedagogico_id IS NULL THEN
    RAISE EXCEPTION 'Nível SOCIOPEDAGOGICO não encontrado.';
  END IF;


  -- ==========================================================
  -- COORDENADOR
  -- ==========================================================

  IF NOT EXISTS (
    SELECT 1
    FROM adra.nivel_permissao_modulo
    WHERE nivel_permissao_id = v_coordenador_id
      AND modulo_id = v_modulo_id
  ) THEN

    INSERT INTO adra.nivel_permissao_modulo (
      nivel_permissao_id,
      modulo_id,
      ordem,
      eh_padrao
    )
    VALUES (
      v_coordenador_id,
      v_modulo_id,
      8,
      false
    );

  END IF;


  -- ==========================================================
  -- SOCIOPEDAGÓGICO
  -- ==========================================================

  IF NOT EXISTS (
    SELECT 1
    FROM adra.nivel_permissao_modulo
    WHERE nivel_permissao_id = v_sociopedagogico_id
      AND modulo_id = v_modulo_id
  ) THEN

    INSERT INTO adra.nivel_permissao_modulo (
      nivel_permissao_id,
      modulo_id,
      ordem,
      eh_padrao
    )
    VALUES (
      v_sociopedagogico_id,
      v_modulo_id,
      3,
      false
    );

  END IF;

END $$;


-- ============================================================
-- MIGRAÇÃO V7: CPF obrigatório e único em Responsável
-- ============================================================

SET search_path TO adra, public;

ALTER TABLE adra.responsavel
  ALTER COLUMN cpf SET NOT NULL;

ALTER TABLE adra.responsavel
  ADD CONSTRAINT uq_responsavel_cpf UNIQUE (cpf);