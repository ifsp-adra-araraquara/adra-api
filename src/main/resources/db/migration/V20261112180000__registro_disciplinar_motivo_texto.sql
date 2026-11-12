-- E-mail da ADRA de 11/11: o registro disciplinar passa a ter só o nível
-- (ocorrência, advertência ou suspensão) e o motivo escrito à mão. Os motivos
-- cadastrados (motivo_id) continuam nos registros antigos.
ALTER TABLE adra.registro_disciplinar
    ADD COLUMN motivo_texto varchar(200);

COMMENT ON COLUMN adra.registro_disciplinar.motivo_texto IS
    'Motivo escrito por quem registrou (registros a partir de 11/2026); os antigos usam motivo_id.';

-- Revisão de segurança: a tabela de histórico de turma ainda é lida pela API
-- e era a única do schema sem RLS (a API usa o papel dono, que não é afetado).
ALTER TABLE adra.assistido_turma_historico ENABLE ROW LEVEL SECURITY;
