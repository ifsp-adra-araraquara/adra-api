-- FT-15: presença e registro disciplinar deixam de apontar para a aula.
-- Base de teste: presenças lançadas por aula (sem chamada) saem; o FT-23 limpa o resto.
-- A tabela aula continua até o calendário deixar de usá-la.

DELETE FROM adra.presenca WHERE chamada_id IS NULL;

ALTER TABLE adra.presenca DROP CONSTRAINT IF EXISTS uq_presenca_aula_assistido;
ALTER TABLE adra.presenca DROP COLUMN IF EXISTS aula_id;
ALTER TABLE adra.presenca ALTER COLUMN chamada_id SET NOT NULL;

ALTER TABLE adra.registro_disciplinar DROP COLUMN IF EXISTS aula_id;
