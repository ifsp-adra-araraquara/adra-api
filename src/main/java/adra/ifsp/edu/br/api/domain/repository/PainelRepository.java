package adra.ifsp.edu.br.api.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * FT-05 — consultas de agregação do Painel do Coordenador, em SQL nativo.
 *
 * <p>Cada método é UMA consulta que devolve números já somados pelo
 * Postgres (GROUP BY / FILTER / janelas), nunca entidades: o painel não
 * carrega alunos nem presenças na memória da API. Índices em
 * V20261026200504__indices_dashboard.sql.
 *
 * <p>Regras iguais às da chamada (ChamadaService/IndicadorFrequencia):
 * <ul>
 *   <li>presença é esparsa — sem linha em {@code presenca} = PRESENTE;</li>
 *   <li>atendido esperado numa chamada = vínculo com o grupo cobrindo a data
 *       ({@code turma_aluno}) e assistido elegível (ativo, ou desligado
 *       depois da data — CA-70.1);</li>
 *   <li>só PRESENTE conta como presença; FJ é ausência.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class PainelRepository {

    private final JdbcClient jdbc;

    /**
     * Filtro opcional de oficina (null = todas). O CAST tipa o parâmetro
     * para o Postgres mesmo quando ele vem nulo.
     */
    private static final String NA_OFICINA = """
            (CAST(:oficinaId AS bigint) IS NULL OR EXISTS (
                SELECT 1 FROM adra.oficina_turma ot
                 WHERE ot.turma_id = t.turma_id AND ot.ativo AND ot.oficina_id = :oficinaId))
            """;

    /** Assistido com vínculo (em qualquer data) num grupo da oficina filtrada; %s = coluna do assistido. */
    private static final String ASSISTIDO_NA_OFICINA = """
            (CAST(:oficinaId AS bigint) IS NULL OR EXISTS (
                SELECT 1 FROM adra.turma_aluno ta
                  JOIN adra.oficina_turma ot ON ot.turma_id = ta.turma_id AND ot.ativo
                 WHERE ta.assistido_id = %s AND ot.oficina_id = :oficinaId))
            """;

    /** Atendido esperado numa chamada lançada (CTE reaproveitada pelas consultas de frequência). */
    private static final String ESPERADOS = """
            esperados AS (
                SELECT DISTINCT c.chamada_id, c.turma_id, c.data_chamada, ta.assistido_id
                  FROM adra.chamada c
                  JOIN adra.turma t ON t.turma_id = c.turma_id
                  JOIN adra.turma_aluno ta
                    ON ta.turma_id = c.turma_id
                   AND ta.data_entrada <= c.data_chamada
                   AND (ta.data_saida IS NULL OR ta.data_saida >= c.data_chamada)
                  JOIN adra.assistido s
                    ON s.assistido_id = ta.assistido_id
                   AND (s.status = 'ATIVO' OR s.data_saida > c.data_chamada)
                 WHERE c.data_chamada BETWEEN :inicio AND :fim
                   AND """ + NA_OFICINA + """
            )
            """;

    public record FrequenciaTurmaMes(LocalDate mes, Long turmaId, long chamadas, long esperados, long faltas, long justificadas) {
    }

    /**
     * Frequência por (mês, turma) numa janela de meses. Uma consulta só
     * alimenta o mês atual, o anterior (variação), a tendência de 6 meses
     * e as barras por turma.
     */
    public List<FrequenciaTurmaMes> frequenciaPorTurmaEMes(LocalDate inicio, LocalDate fim, Long oficinaId) {
        return jdbc.sql("WITH " + ESPERADOS + """
                        SELECT date_trunc('month', e.data_chamada)::date AS mes,
                               e.turma_id,
                               COUNT(DISTINCT e.chamada_id) AS chamadas,
                               COUNT(*) AS esperados,
                               COUNT(*) FILTER (WHERE p.status_presenca = 'FALTA') AS faltas,
                               COUNT(*) FILTER (WHERE p.status_presenca = 'FALTA_JUSTIFICADA') AS justificadas
                          FROM esperados e
                          LEFT JOIN adra.presenca p
                            ON p.chamada_id = e.chamada_id AND p.assistido_id = e.assistido_id
                         GROUP BY 1, 2
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("oficinaId", oficinaId)
                .query((rs, i) -> new FrequenciaTurmaMes(
                        rs.getObject("mes", LocalDate.class),
                        rs.getLong("turma_id"),
                        rs.getLong("chamadas"),
                        rs.getLong("esperados"),
                        rs.getLong("faltas"),
                        rs.getLong("justificadas")))
                .list();
    }

    public record TurmaResumo(Long turmaId, String nomeTurma, String nomeOficina, Integer capacidade,
                              long alunos, long chamadasLancadas) {
    }

    /**
     * Grupos ativos com atendidos na data de referência e as chamadas lançadas
     * no mês. Subconsultas por grupo em vez de dois JOINs, para a contagem de
     * atendidos não multiplicar pela de chamadas. Pendentes vêm do StatusChamadaService.
     */
    public List<TurmaResumo> turmas(LocalDate inicio, LocalDate fim, LocalDate referencia, Long oficinaId) {
        return jdbc.sql("""
                        SELECT t.turma_id, t.nome_turma, t.capacidade,
                               (SELECT string_agg(o.nome_oficina, ', ' ORDER BY o.nome_oficina)
                                  FROM adra.oficina_turma ot
                                  JOIN adra.oficina o ON o.oficina_id = ot.oficina_id
                                 WHERE ot.turma_id = t.turma_id AND ot.ativo) AS nome_oficina,
                               (SELECT COUNT(DISTINCT ta.assistido_id)
                                  FROM adra.turma_aluno ta
                                  JOIN adra.assistido s ON s.assistido_id = ta.assistido_id
                                 WHERE ta.turma_id = t.turma_id
                                   AND ta.data_entrada <= :referencia
                                   AND (ta.data_saida IS NULL OR ta.data_saida >= :referencia)
                                   AND (s.status = 'ATIVO' OR s.data_saida > :referencia)) AS alunos,
                               COALESCE(c.lancadas, 0) AS lancadas
                          FROM adra.turma t
                          LEFT JOIN (
                                SELECT turma_id, COUNT(*) AS lancadas
                                  FROM adra.chamada
                                 WHERE data_chamada BETWEEN :inicio AND :fim
                                 GROUP BY turma_id
                          ) c ON c.turma_id = t.turma_id
                         WHERE t.ativo AND """ + NA_OFICINA + """
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("referencia", referencia)
                .param("oficinaId", oficinaId)
                .query((rs, i) -> new TurmaResumo(
                        rs.getLong("turma_id"),
                        rs.getString("nome_turma"),
                        rs.getString("nome_oficina"),
                        (Integer) rs.getObject("capacidade"),
                        rs.getLong("alunos"),
                        rs.getLong("lancadas")))
                .list();
    }

    public record AssistidosResumo(long ativos, long ativosAntes, long entradas, long saidas,
                                   long ate5, long de6a9, long de10a13, long de14a17, long de18, long semData) {
    }

    /**
     * Ativos na referência e no fim do mês anterior, entradas/saídas do mês e
     * faixa etária na referência — tudo num único passe com FILTER.
     * Sem data de entrada, vale a data de cadastro.
     */
    public AssistidosResumo assistidos(LocalDate inicio, LocalDate fim, LocalDate referencia, LocalDate fimMesAnterior,
                                       Long oficinaId) {
        return jdbc.sql("""
                        WITH base AS (
                            SELECT COALESCE(data_entrada, criado_em::date) AS entrada,
                                   status, data_saida,
                                   EXTRACT(YEAR FROM age(:referencia, data_nascimento))::int AS idade,
                                   (COALESCE(data_entrada, criado_em::date) <= :referencia
                                       AND (status = 'ATIVO' OR data_saida > :referencia)) AS ativo_ref,
                                   (COALESCE(data_entrada, criado_em::date) <= :fimAnterior
                                       AND (status = 'ATIVO' OR data_saida > :fimAnterior)) AS ativo_antes
                              FROM adra.assistido s
                             WHERE """ + ASSISTIDO_NA_OFICINA.formatted("s.assistido_id") + """
                        )
                        SELECT COUNT(*) FILTER (WHERE ativo_ref) AS ativos,
                               COUNT(*) FILTER (WHERE ativo_antes) AS ativos_antes,
                               COUNT(*) FILTER (WHERE entrada BETWEEN :inicio AND :fim) AS entradas,
                               COUNT(*) FILTER (WHERE data_saida BETWEEN :inicio AND :fim) AS saidas,
                               COUNT(*) FILTER (WHERE ativo_ref AND idade <= 5) AS ate5,
                               COUNT(*) FILTER (WHERE ativo_ref AND idade BETWEEN 6 AND 9) AS de6a9,
                               COUNT(*) FILTER (WHERE ativo_ref AND idade BETWEEN 10 AND 13) AS de10a13,
                               COUNT(*) FILTER (WHERE ativo_ref AND idade BETWEEN 14 AND 17) AS de14a17,
                               COUNT(*) FILTER (WHERE ativo_ref AND idade >= 18) AS de18,
                               COUNT(*) FILTER (WHERE ativo_ref AND idade IS NULL) AS sem_data
                          FROM base
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("referencia", referencia)
                .param("fimAnterior", fimMesAnterior)
                .param("oficinaId", oficinaId)
                .query((rs, i) -> new AssistidosResumo(
                        rs.getLong("ativos"), rs.getLong("ativos_antes"),
                        rs.getLong("entradas"), rs.getLong("saidas"),
                        rs.getLong("ate5"), rs.getLong("de6a9"), rs.getLong("de10a13"),
                        rs.getLong("de14a17"), rs.getLong("de18"), rs.getLong("sem_data")))
                .single();
    }

    public record DisciplinaNivel(String nivel, long noMes, long noMesAnterior) {
    }

    /** Registros (não arquivados) por nível no mês e no anterior — a janela pega os dois de uma vez. */
    public List<DisciplinaNivel> disciplinaPorNivel(LocalDate inicioAnterior, LocalDate inicio, LocalDate fim, Long oficinaId) {
        return jdbc.sql("""
                        SELECT nivel,
                               COUNT(*) FILTER (WHERE data_registro >= :inicio) AS no_mes,
                               COUNT(*) FILTER (WHERE data_registro < :inicio) AS no_mes_anterior
                          FROM adra.registro_disciplinar r
                         WHERE arquivado = false
                           AND data_registro BETWEEN :inicioAnterior AND :fim
                           AND """ + ASSISTIDO_NA_OFICINA.formatted("r.assistido_id") + """
                         GROUP BY nivel
                        """)
                .param("inicioAnterior", inicioAnterior)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("oficinaId", oficinaId)
                .query((rs, i) -> new DisciplinaNivel(rs.getString("nivel"), rs.getLong("no_mes"), rs.getLong("no_mes_anterior")))
                .list();
    }

    public record OficinasResumo(long ativas, long novas) {
    }

    /** Oficinas ativas hoje e criadas no mês (cartão "Oficinas ativas · N novas"). */
    public OficinasResumo oficinas(LocalDate inicio, LocalDate fim) {
        return jdbc.sql("""
                        SELECT COUNT(*) FILTER (WHERE ativo) AS ativas,
                               COUNT(*) FILTER (WHERE criado_em::date BETWEEN :inicio AND :fim) AS novas
                          FROM adra.oficina
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .query((rs, i) -> new OficinasResumo(rs.getLong("ativas"), rs.getLong("novas")))
                .single();
    }

    /* ======================= Precisa de atenção hoje ======================= */

    public record FaltasSeguidas(Long assistidoId, String nome, String turma, long faltas, LocalDate ultimaChamada) {
    }

    /**
     * Ativos com N+ faltas (não justificadas) seguidas nas chamadas mais
     * recentes. Numera as chamadas de cada atendido da mais nova para a mais
     * antiga e conta as faltas antes da primeira "quebra" (P ou FJ) — a
     * soma acumulada de quebras é 0 exatamente nesse trecho inicial.
     */
    public List<FaltasSeguidas> faltasSeguidas(LocalDate desde, LocalDate hoje, int limiar) {
        return jdbc.sql("""
                        WITH chamadas_do_atendido AS (
                            SELECT DISTINCT ta.assistido_id, ch.chamada_id, ch.data_chamada,
                                   COALESCE(p.status_presenca::text, 'PRESENTE') AS status,
                                   DENSE_RANK() OVER (PARTITION BY ta.assistido_id
                                                      ORDER BY ch.data_chamada DESC, ch.chamada_id DESC) AS ordem
                              FROM adra.turma_aluno ta
                              JOIN adra.assistido s ON s.assistido_id = ta.assistido_id AND s.status = 'ATIVO'
                              JOIN adra.chamada ch
                                ON ch.turma_id = ta.turma_id
                               AND ch.data_chamada BETWEEN :desde AND :hoje
                               AND ch.data_chamada >= ta.data_entrada
                               AND (ta.data_saida IS NULL OR ch.data_chamada <= ta.data_saida)
                              LEFT JOIN adra.presenca p
                                ON p.chamada_id = ch.chamada_id AND p.assistido_id = ta.assistido_id
                        ),
                        com_quebras AS (
                            SELECT *, SUM(CASE WHEN status <> 'FALTA' THEN 1 ELSE 0 END)
                                          OVER (PARTITION BY assistido_id ORDER BY ordem) AS quebras
                              FROM chamadas_do_atendido
                        )
                        SELECT c.assistido_id, s.nome_completo, t.nome_turma,
                               COUNT(*) AS faltas, MAX(c.data_chamada) AS ultima
                          FROM com_quebras c
                          JOIN adra.assistido s ON s.assistido_id = c.assistido_id
                          LEFT JOIN adra.turma t ON t.turma_id = s.turma_id
                         WHERE c.quebras = 0
                         GROUP BY c.assistido_id, s.nome_completo, t.nome_turma
                        HAVING COUNT(*) >= :limiar
                         ORDER BY faltas DESC, s.nome_completo
                        """)
                .param("desde", desde)
                .param("hoje", hoje)
                .param("limiar", limiar)
                .query((rs, i) -> new FaltasSeguidas(
                        rs.getLong("assistido_id"), rs.getString("nome_completo"), rs.getString("nome_turma"),
                        rs.getLong("faltas"), rs.getObject("ultima", LocalDate.class)))
                .list();
    }

    public record UmPasso(Long assistidoId, String nome, String nivel, long registros) {
    }

    /**
     * Ativos com contagem do nível ≡ 2 (mod 3): o próximo registro dispara
     * a recomendação (FT-22 usa a sequência % 3 sobre não arquivados).
     */
    public List<UmPasso> umPassoDaProgressao() {
        return jdbc.sql("""
                        SELECT r.assistido_id, s.nome_completo, r.nivel, COUNT(*) AS registros
                          FROM adra.registro_disciplinar r
                          JOIN adra.assistido s ON s.assistido_id = r.assistido_id AND s.status = 'ATIVO'
                         WHERE r.arquivado = false
                         GROUP BY r.assistido_id, s.nome_completo, r.nivel
                        HAVING COUNT(*) % 3 = 2
                         ORDER BY s.nome_completo
                        """)
                .query((rs, i) -> new UmPasso(
                        rs.getLong("assistido_id"), rs.getString("nome_completo"),
                        rs.getString("nivel"), rs.getLong("registros")))
                .list();
    }

    public record Aniversariante(Long assistidoId, String nome, String turma, LocalDate nascimento) {
    }

    /** Ativos cujo aniversário (MM-DD) está entre os dias informados. */
    public List<Aniversariante> aniversariantes(Collection<String> diasMesDia) {
        return jdbc.sql("""
                        SELECT s.assistido_id, s.nome_completo, t.nome_turma, s.data_nascimento
                          FROM adra.assistido s
                          LEFT JOIN adra.turma t ON t.turma_id = s.turma_id
                         WHERE s.status = 'ATIVO'
                           AND to_char(s.data_nascimento, 'MM-DD') IN (:dias)
                        """)
                .param("dias", diasMesDia)
                .query((rs, i) -> new Aniversariante(
                        rs.getLong("assistido_id"), rs.getString("nome_completo"),
                        rs.getString("nome_turma"), rs.getObject("data_nascimento", LocalDate.class)))
                .list();
    }

    public record Atividade(String tipo, Long referenciaId, Long assistidoId, String titulo, String detalhe,
                            Instant quando) {
    }

    /**
     * Últimos acontecimentos, direto das tabelas de domínio (o log de
     * auditoria grava uma linha por falta e viraria ruído): chamada lançada
     * (referenciaId = grupo, detalhe = data yyyy-MM-dd), registro disciplinar
     * manual e assistido cadastrado.
     * Cada ramo já vem ordenado e limitado; o conjunto final é cortado em
     * `limite`. Sem texto livre (a descrição disciplinar fica fora — DT-01).
     */
    public List<Atividade> atividadesRecentes(int limite) {
        return jdbc.sql("""
                        (SELECT 'CHAMADA' AS tipo, c.turma_id AS ref, NULL::bigint AS assistido_id,
                                t.nome_turma AS titulo, to_char(c.data_chamada, 'YYYY-MM-DD') AS detalhe,
                                c.atualizado_em AS quando
                           FROM adra.chamada c JOIN adra.turma t ON t.turma_id = c.turma_id
                          ORDER BY c.atualizado_em DESC LIMIT :limite)
                        UNION ALL
                        (SELECT 'DISCIPLINAR', r.registro_id, r.assistido_id,
                                s.nome_completo, r.nivel, r.criado_em
                           FROM adra.registro_disciplinar r JOIN adra.assistido s ON s.assistido_id = r.assistido_id
                          WHERE r.arquivado = false AND r.origem = 'MANUAL'
                          ORDER BY r.criado_em DESC LIMIT :limite)
                        UNION ALL
                        (SELECT 'ASSISTIDO', s.assistido_id, s.assistido_id,
                                s.nome_completo, NULL, s.criado_em
                           FROM adra.assistido s
                          ORDER BY s.criado_em DESC LIMIT :limite)
                        ORDER BY quando DESC NULLS LAST
                        LIMIT :limite
                        """)
                .param("limite", limite)
                .query((rs, i) -> new Atividade(
                        rs.getString("tipo"),
                        rs.getLong("ref"),
                        (Long) rs.getObject("assistido_id"),
                        rs.getString("titulo"),
                        rs.getString("detalhe"),
                        rs.getTimestamp("quando") == null ? null : rs.getTimestamp("quando").toInstant()))
                .list();
    }
}
