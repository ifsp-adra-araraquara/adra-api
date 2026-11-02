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

    private final JdbcClient jdbc;

    /**
     * @param diasIso dias da semana ISO (1 = segunda … 7 = domingo) separados por vírgula, ou null
     * @param alunos  alunos com vínculo vigente e elegíveis na data de referência
     */
    public record TurmaDaOficina(Long oficinaId, Long turmaId, Integer capacidade,
                                 LocalTime horarioInicio, String diasIso, long alunos) {
    }

    /** Turmas ativas com oficina. Dias = dias de chamada do grupo (turma_dia_semana); horário = o da turma. */
    public List<TurmaDaOficina> turmasAtivas(LocalDate referencia) {
        return jdbc.sql("""
                        SELECT t.oficina_id, t.turma_id, t.capacidade,
                               t.horario_inicio AS horario,
                               (SELECT string_agg(CASE d.dia_semana
                                           WHEN 'MONDAY' THEN '1' WHEN 'TUESDAY' THEN '2'
                                           WHEN 'WEDNESDAY' THEN '3' WHEN 'THURSDAY' THEN '4'
                                           WHEN 'FRIDAY' THEN '5' WHEN 'SATURDAY' THEN '6'
                                           ELSE '7' END, ',')
                                  FROM adra.turma_dia_semana d
                                 WHERE d.turma_id = t.turma_id) AS dias,
                               (SELECT COUNT(*)
                                  FROM adra.turma_aluno ta
                                  JOIN adra.assistido s ON s.assistido_id = ta.assistido_id
                                 WHERE ta.turma_id = t.turma_id
                                   AND ta.data_entrada <= :referencia
                                   AND (ta.data_saida IS NULL OR ta.data_saida >= :referencia)
                                   AND (s.status = 'ATIVO' OR s.data_saida > :referencia)) AS alunos
                          FROM adra.turma t
                         WHERE t.ativo AND t.oficina_id IS NOT NULL
                        """)
                .param("referencia", referencia)
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
