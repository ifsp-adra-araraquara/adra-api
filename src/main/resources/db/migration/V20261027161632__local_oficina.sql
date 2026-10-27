-- Onde a oficina acontece (ex.: "Sala 1", "Quadra", "Lab. Info").
-- Opcional: oficinas já cadastradas ficam sem local até alguém editar.
ALTER TABLE adra.oficina ADD COLUMN local VARCHAR(100);
