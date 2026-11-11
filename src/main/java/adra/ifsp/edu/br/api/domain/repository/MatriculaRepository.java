package adra.ifsp.edu.br.api.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import adra.ifsp.edu.br.api.domain.model.Matricula;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    Optional<Matricula> findByAssistido_AssistidoIdAndAno(Long assistidoId, Integer ano);

    /** Histórico do atendido, do ano mais recente para o mais antigo. */
    List<Matricula> findByAssistido_AssistidoIdOrderByAnoDesc(Long assistidoId);

    // ---------------------------------------------------------------
    // Encerramento do ano: prévia e atualizações em lote (SQL direto,
    // poucas instruções para o ano inteiro em vez de uma por atendido).
    // ---------------------------------------------------------------

    /** Atendido que estava ativo no ano e não tem matrícula no seguinte. */
    interface NaoRenovou {
        Long getAssistidoId();

        String getNomeCompleto();

        String getNomeTurma();
    }

    @Query(value = "select count(*) from adra.matricula where ano = :ano and status = 'ATIVA'", nativeQuery = true)
    int contarAtivas(@Param("ano") int ano);

    @Query(value = """
            select count(*) from adra.matricula m
            where m.ano = :ano and m.status = 'ATIVA'
              and exists (select 1 from adra.matricula s where s.assistido_id = m.assistido_id and s.ano = :ano + 1)
            """, nativeQuery = true)
    int contarRenovaram(@Param("ano") int ano);

    /** Matrículas do ano seguinte de quem não tinha matrícula no ano (famílias novas). */
    @Query(value = """
            select count(*) from adra.matricula s
            where s.ano = :ano + 1
              and not exists (select 1 from adra.matricula m where m.assistido_id = s.assistido_id and m.ano = :ano)
            """, nativeQuery = true)
    int contarNovosNoAnoSeguinte(@Param("ano") int ano);

    @Query(value = """
            select a.assistido_id as assistidoId, a.nome_completo as nomeCompleto, t.nome_turma as nomeTurma
            from adra.matricula m
            join adra.assistido a on a.assistido_id = m.assistido_id
            left join adra.turma t on t.turma_id = a.turma_id
            where m.ano = :ano and m.status = 'ATIVA'
              and not exists (select 1 from adra.matricula s where s.assistido_id = m.assistido_id and s.ano = :ano + 1)
            order by a.nome_completo
            """, nativeQuery = true)
    List<NaoRenovou> listarNaoRenovaram(@Param("ano") int ano);

    /** Quem não renovou perde a vaga: sai da ADRA no último dia do ano. Rodar antes de encerrar as matrículas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.assistido a
               set status = 'INATIVO', data_saida = :ultimoDia, motivo_saida = :motivo, turma_id = null,
                   atualizado_em = now()
             where a.status = 'ATIVO'
               and exists (select 1 from adra.matricula m
                           where m.assistido_id = a.assistido_id and m.ano = :ano and m.status = 'ATIVA')
               and not exists (select 1 from adra.matricula s where s.assistido_id = a.assistido_id and s.ano = :ano + 1)
            """, nativeQuery = true)
    int desligarQuemNaoRenovou(@Param("ano") int ano, @Param("ultimoDia") LocalDate ultimoDia,
                               @Param("motivo") String motivo);

    /** Quem tem matrícula no ano seguinte passa a ter ela como atual, com o grupo dela. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.assistido a
               set matricula_atual_id = s.matricula_id,
                   turma_id = (select ta.turma_id from adra.turma_aluno ta
                               where ta.matricula_id = s.matricula_id and ta.data_saida is null
                               order by ta.data_entrada desc limit 1),
                   atualizado_em = now()
              from adra.matricula s
             where s.assistido_id = a.assistido_id and s.ano = :ano + 1
            """, nativeQuery = true)
    int passarParaOAnoSeguinte(@Param("ano") int ano);

    /** Fecha os vínculos de grupo ainda abertos do ano no último dia dele. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.turma_aluno
               set data_saida = :ultimoDia, status = 'INATIVO', atualizado_em = now()
             where data_saida is null
               and matricula_id in (select matricula_id from adra.matricula where ano = :ano)
            """, nativeQuery = true)
    int fecharVinculosDoAno(@Param("ano") int ano, @Param("ultimoDia") LocalDate ultimoDia);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.matricula set status = 'ENCERRADA', atualizado_em = now()
             where ano = :ano and status = 'ATIVA'
            """, nativeQuery = true)
    int encerrarMatriculasDoAno(@Param("ano") int ano);

    /** Ano mais antigo, até o informado, que ainda tem matrícula ativa (candidato a encerrar). */
    @Query(value = "select min(ano) from adra.matricula where status = 'ATIVA' and ano <= :ateAno", nativeQuery = true)
    Integer menorAnoComMatriculaAtiva(@Param("ateAno") int ateAno);

    // ---------------------------------------------------------------
    // Lista anual (histórico): uma consulta para o ano inteiro.
    // ---------------------------------------------------------------

    interface LinhaDoAno {
        Long getAssistidoId();

        String getNomeCompleto();

        LocalDate getDataNascimento();

        String getStatusMatricula();

        String getNomeTurma();

        LocalDate getEntrada();

        LocalDate getSaida();

        int getTotalChamadas();

        int getPresencas();

        int getFaltas();

        int getFaltasJustificadas();
    }

    /**
     * Atendidos matriculados no ano, com o último grupo do ano e a frequência só
     * das chamadas daquele ano (até {@code ate}: hoje, para o ano em curso).
     * Presença é esparsa: chamada sem linha em presenca = presente.
     */
    @Query(value = """
            WITH alvo AS (
                SELECT m.matricula_id, m.assistido_id, m.status
                  FROM adra.matricula m
                 WHERE m.ano = :ano
            ),
            vinculos AS (
                SELECT ta.turma_aluno_id, ta.matricula_id, ta.assistido_id, ta.turma_id, ta.data_entrada, ta.data_saida,
                       a.data_saida AS saida_da_adra
                  FROM adra.turma_aluno ta
                  JOIN alvo ON alvo.matricula_id = ta.matricula_id
                  JOIN adra.assistido a ON a.assistido_id = ta.assistido_id
            ),
            ultimo_grupo AS (
                SELECT DISTINCT ON (v.matricula_id) v.matricula_id, t.nome_turma
                  FROM vinculos v
                  JOIN adra.turma t ON t.turma_id = v.turma_id
                 -- troca de grupo no mesmo dia: vale o vínculo criado por último
                 ORDER BY v.matricula_id, v.data_entrada DESC, v.turma_aluno_id DESC
            ),
            entrada AS (
                SELECT matricula_id, MIN(data_entrada) AS data_entrada FROM vinculos GROUP BY matricula_id
            ),
            chamadas AS (
                SELECT DISTINCT v.matricula_id, v.assistido_id, c.chamada_id
                  FROM vinculos v
                  JOIN adra.chamada c
                    ON c.turma_id = v.turma_id
                   AND c.data_chamada >= v.data_entrada
                   AND c.data_chamada <= LEAST(COALESCE(v.data_saida, :ate), :ate)
                   AND c.data_chamada BETWEEN :inicioDoAno AND :fimDoAno
                   -- mesma regra da chamada: desligado deixa de contar a partir do dia da saída
                   AND (v.saida_da_adra IS NULL OR c.data_chamada < v.saida_da_adra)
            ),
            frequencia AS (
                SELECT ch.matricula_id,
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE p.presenca_id IS NULL
                                           OR CAST(p.status_presenca AS text) = 'PRESENTE') AS presencas,
                       COUNT(*) FILTER (WHERE CAST(p.status_presenca AS text) = 'FALTA') AS faltas,
                       COUNT(*) FILTER (WHERE CAST(p.status_presenca AS text) = 'FALTA_JUSTIFICADA') AS justificadas
                  FROM chamadas ch
                  LEFT JOIN adra.presenca p ON p.chamada_id = ch.chamada_id AND p.assistido_id = ch.assistido_id
                 GROUP BY ch.matricula_id
            )
            SELECT a.assistido_id                    AS "assistidoId",
                   a.nome_completo                   AS "nomeCompleto",
                   a.data_nascimento                 AS "dataNascimento",
                   alvo.status                       AS "statusMatricula",
                   g.nome_turma                      AS "nomeTurma",
                   e.data_entrada                    AS "entrada",
                   CASE WHEN a.data_saida BETWEEN :inicioDoAno AND :fimDoAno THEN a.data_saida END AS "saida",
                   COALESCE(f.total, 0)              AS "totalChamadas",
                   COALESCE(f.presencas, 0)          AS "presencas",
                   COALESCE(f.faltas, 0)             AS "faltas",
                   COALESCE(f.justificadas, 0)       AS "faltasJustificadas"
              FROM alvo
              JOIN adra.assistido a ON a.assistido_id = alvo.assistido_id
              LEFT JOIN ultimo_grupo g ON g.matricula_id = alvo.matricula_id
              LEFT JOIN entrada e ON e.matricula_id = alvo.matricula_id
              LEFT JOIN frequencia f ON f.matricula_id = alvo.matricula_id
             ORDER BY g.nome_turma NULLS LAST, a.nome_completo
            """, nativeQuery = true)
    List<LinhaDoAno> listarDoAno(@Param("ano") int ano, @Param("inicioDoAno") LocalDate inicioDoAno,
                                 @Param("fimDoAno") LocalDate fimDoAno, @Param("ate") LocalDate ate);

    /** Anos que têm matrícula, do mais recente para o mais antigo (filtro da lista anual). */
    @Query(value = "select distinct ano from adra.matricula order by ano desc", nativeQuery = true)
    List<Integer> anosComMatricula();
}
