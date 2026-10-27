package adra.ifsp.edu.br.api.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Dados de turma que os cards de oficina mostram (dias, horário, vagas),
 * numa consulta só para todas as oficinas — a tela não busca as turmas de
 * cada oficina uma a uma.
 */
@Repository
@RequiredArgsConstructor
public class OficinaResumoRepository {

    /** Janela de aulas, para cada lado de hoje, usada para descobrir dias e horário da turma. */
    private static final int SEMANAS_JANELA = 8;

    private final JdbcClient jdbc;

    /**
     * @param diasIso dias da semana ISO (1 = segunda … 7 = domingo) separados por vírgula, ou null
     * @param alunos  alunos com vínculo vigente e elegíveis na data de referência
     */
    public record TurmaDaOficina(Long oficinaId, Long turmaId, Integer capacidade,
                                 LocalTime horarioInicio, String diasIso, long alunos) {
    }

    /**
     * Turmas ativas com oficina. Dias e horário saem das aulas planejadas
     * ou realizadas de {@value #SEMANAS_JANELA} semanas antes a
     * {@value #SEMANAS_JANELA} depois de hoje — é nas aulas que a geração
     * grava o horário; os campos da própria turma costumam ficar vazios e
     * só valem como reserva. Um dia da semana só conta com 2 aulas ou mais
     * nele, para uma aula avulsa (reposição) não mudar a agenda da turma;
     * o horário é o mais frequente (moda) na janela.
     */
    public List<TurmaDaOficina> turmasAtivas(LocalDate referencia) {
        return jdbc.sql("""
                        SELECT t.oficina_id, t.turma_id, t.capacidade,
                               COALESCE(a.horario, t.horario_inicio) AS horario,
                               COALESCE(a.dias,
                                        (SELECT string_agg(CASE d.dia_semana
                                                    WHEN 'MONDAY' THEN '1' WHEN 'TUESDAY' THEN '2'
                                                    WHEN 'WEDNESDAY' THEN '3' WHEN 'THURSDAY' THEN '4'
                                                    WHEN 'FRIDAY' THEN '5' WHEN 'SATURDAY' THEN '6'
                                                    ELSE '7' END, ',')
                                           FROM adra.turma_dia_semana d
                                          WHERE d.turma_id = t.turma_id)) AS dias,
                               (SELECT COUNT(*)
                                  FROM adra.turma_aluno ta
                                  JOIN adra.assistido s ON s.assistido_id = ta.assistido_id
                                 WHERE ta.turma_id = t.turma_id
                                   AND ta.data_entrada <= :referencia
                                   AND (ta.data_saida IS NULL OR ta.data_saida >= :referencia)
                                   AND (s.status = 'ATIVO' OR s.data_saida > :referencia)) AS alunos
                          FROM adra.turma t
                          LEFT JOIN (
                                SELECT turma_id,
                                       string_agg(DISTINCT dia::text, ',' ORDER BY dia::text) FILTER (WHERE aulas_no_dia >= 2) AS dias,
                                       mode() WITHIN GROUP (ORDER BY horario) AS horario
                                  FROM (SELECT turma_id, horario_inicio AS horario,
                                               EXTRACT(ISODOW FROM data_aula)::int AS dia,
                                               COUNT(*) OVER (PARTITION BY turma_id, EXTRACT(ISODOW FROM data_aula)) AS aulas_no_dia
                                          FROM adra.aula
                                         WHERE status_aula IN ('PLANEJADA', 'REALIZADA')
                                           AND data_aula BETWEEN :inicio AND :fim) x
                                 GROUP BY turma_id
                          ) a ON a.turma_id = t.turma_id
                         WHERE t.ativo AND t.oficina_id IS NOT NULL
                        """)
                .param("referencia", referencia)
                .param("inicio", referencia.minusWeeks(SEMANAS_JANELA))
                .param("fim", referencia.plusWeeks(SEMANAS_JANELA))
                .query((rs, i) -> new TurmaDaOficina(
                        rs.getLong("oficina_id"),
                        rs.getLong("turma_id"),
                        rs.getObject("capacidade", Integer.class),
                        rs.getObject("horario", LocalTime.class),
                        rs.getString("dias"),
                        rs.getLong("alunos")))
                .list();
    }
}
