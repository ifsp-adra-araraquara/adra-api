-- Em migration separada: valor novo de enum não pode ser usado na mesma transação em que é criado.
ALTER TYPE adra.modulo_sistema ADD VALUE IF NOT EXISTS 'DISCIPLINA';
