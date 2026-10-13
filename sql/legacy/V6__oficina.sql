-- ============================================================
-- MIGRACAO V6: Modulo Oficinas (US-13)
-- ============================================================
-- Nota: a tabela `turma` (usada como referencia de padrao para esta
-- migracao) nao aparece em nenhuma migration rastreada no repo -
-- provavelmente foi criada direto via Supabase Studio/CLI. Vale
-- alinhar com o time e, se for o caso, recriar uma migration para ela
-- tambem, para manter o schema versionado.

SET search_path TO adra, public;

CREATE TABLE IF NOT EXISTS adra.oficina (
    oficina_id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome_oficina            varchar(100) NOT NULL,
    oficineiro_responsavel  varchar(150) NOT NULL,
    ativo                   boolean NOT NULL DEFAULT true,
    criado_em               timestamp NOT NULL DEFAULT now(),
    atualizado_em           timestamp NOT NULL DEFAULT now()
);

-- Indice para acelerar a checagem de duplicidade por nome (busca LIKE
-- case-insensitive, feita tanto pelo filtro de listagem quanto pelo
-- alerta nao bloqueante do front) e usa unaccent, ja habilitado na V5.
CREATE INDEX IF NOT EXISTS oficina_nome_idx
    ON adra.oficina (lower(unaccent(nome_oficina)));
