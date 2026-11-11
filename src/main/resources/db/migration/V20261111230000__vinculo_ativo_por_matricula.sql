-- Um grupo aberto por atendido EM CADA MATRÍCULA (antes: um por atendido).
--
-- Na rematrícula o atendido fica com dois vínculos abertos ao mesmo tempo:
-- o do ano em curso (até 31/12) e o do ano seguinte (a partir de 1º/01).
-- A regra continua impedindo dois grupos no mesmo ano.
DROP INDEX IF EXISTS adra.uq_turma_aluno_um_ativo_por_assistido;

CREATE UNIQUE INDEX IF NOT EXISTS uq_turma_aluno_um_ativo_por_matricula
    ON adra.turma_aluno (assistido_id, COALESCE(matricula_id, 0))
    WHERE status = 'ATIVO' AND data_saida IS NULL;
