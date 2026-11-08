-- Reunião 05/11: o serviço de convivência não pode negar atendimento por renda.
-- A renda familiar fica só como estimativa; a Assistente Social avalia na entrevista.
DROP TABLE IF EXISTS adra.parametro_sistema;

COMMENT ON COLUMN adra.assistido.renda_familiar IS 'Renda mensal da familia (R$) - estimativa informada, nao barra o cadastro';
