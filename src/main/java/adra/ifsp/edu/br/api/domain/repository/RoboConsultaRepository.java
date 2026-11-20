package adra.ifsp.edu.br.api.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Robozinho ADRA — consultas de agregação que respondem às perguntas do dia a
 * dia. Como no {@link PainelRepository}, cada método é UMA consulta que devolve
 * números já somados pelo Postgres; nada de carregar alunos na memória.
 *
 * <p>A regra de frequência é a mesma do Painel e da chamada: presença esparsa
 * (sem linha = PRESENTE), atendido esperado = vínculo com o grupo cobrindo a
 * data e assistido elegível, só PRESENTE conta como presença (FJ é ausência).
 *
 * <p>Escopo do Oficineiro: {@code :oficineiroId} nulo = todos os grupos; com
 * valor, só os grupos ligados (oficina_turma ativo) às oficinas dele — a mesma
 * regra do {@code AcessoTurmaService.podeAcessarTurma}.
 */
@Repository
@RequiredArgsConstructor
public class RoboConsultaRepository {

    private final JdbcClient jdbc;

    /** Grupo visível no escopo; %s = coluna com o turma_id. */
    private static final String NO_ESCOPO = """
            (CAST(:oficineiroId AS bigint) IS NULL OR EXISTS (
                SELECT 1 FROM adra.oficina_turma ot
                  JOIN adra.oficina o ON o.oficina_id = ot.oficina_id
                 WHERE ot.turma_id = %s AND ot.ativo AND o.oficineiro_id = :oficineiroId))
            """;

    /** Atendido esperado em cada chamada lançada no intervalo, dentro do escopo. */
    private static final String ESPERADOS = """
            esperados AS (
                SELECT DISTINCT c.chamada_id, c.turma_id, c.data_chamada, ta.assistido_id
                  FROM adra.chamada c
                  JOIN adra.turma_aluno ta
                    ON ta.turma_id = c.turma_id
                   AND ta.data_entrada <= c.data_chamada
                   AND (ta.data_saida IS NULL OR ta.data_saida >= c.data_chamada)
                  JOIN adra.assistido s
                    ON s.assistido_id = ta.assistido_id
                   AND (s.status = 'ATIVO' OR s.data_saida > c.data_chamada)
                 WHERE c.data_chamada BETWEEN :inicio AND :fim
                   AND """ + NO_ESCOPO.formatted("c.turma_id") + """
            )
            """;

    public record Opcao(Long id, String nome, String detalhe) {
    }

    /** Grupos ativos visíveis no escopo, por nome. */
    public List<Opcao> gruposAtivos(Long oficineiroId) {
        return jdbc.sql("""
                        SELECT t.turma_id, t.nome_turma,
                               (SELECT string_agg(o.nome_oficina, ', ' ORDER BY o.nome_oficina)
                                  FROM adra.oficina_turma ot
                                  JOIN adra.oficina o ON o.oficina_id = ot.oficina_id
                                 WHERE ot.turma_id = t.turma_id AND ot.ativo) AS oficinas
                          FROM adra.turma t
                         WHERE t.ativo AND """ + NO_ESCOPO.formatted("t.turma_id") + """
                         ORDER BY t.nome_turma
                        """)
                .param("oficineiroId", oficineiroId)
                .query((rs, i) -> new Opcao(rs.getLong("turma_id"), rs.getString("nome_turma"), rs.getString("oficinas")))
                .list();
    }

    /** Oficinas ativas; para o Oficineiro, só as dele. */
    public List<Opcao> oficinasAtivas(Long oficineiroId) {
        return jdbc.sql("""
                        SELECT o.oficina_id, o.nome_oficina, u.nome_completo AS oficineiro
                          FROM adra.oficina o
                          LEFT JOIN adra.usuario u ON u.usuario_id = o.oficineiro_id
                         WHERE o.ativo
                           AND (CAST(:oficineiroId AS bigint) IS NULL OR o.oficineiro_id = :oficineiroId)
                         ORDER BY o.nome_oficina
                        """)
                .param("oficineiroId", oficineiroId)
                .query((rs, i) -> new Opcao(rs.getLong("oficina_id"), rs.getString("nome_oficina"), rs.getString("oficineiro")))
                .list();
    }

    public record FrequenciaLinha(LocalDate mes, Long turmaId, String nomeTurma, String oficinas,
                                  long chamadas, long esperados, long faltas, long justificadas) {
    }

    /** Frequência por (mês, grupo) no intervalo — base de "frequência do grupo", comparações e ranking. */
    public List<FrequenciaLinha> frequenciaPorTurmaEMes(LocalDate inicio, LocalDate fim, Long oficineiroId) {
        return jdbc.sql("WITH " + ESPERADOS + """
                        SELECT date_trunc('month', e.data_chamada)::date AS mes,
                               e.turma_id, t.nome_turma,
                               (SELECT string_agg(o.nome_oficina, ', ' ORDER BY o.nome_oficina)
                                  FROM adra.oficina_turma ot
                                  JOIN adra.oficina o ON o.oficina_id = ot.oficina_id
                                 WHERE ot.turma_id = e.turma_id AND ot.ativo) AS oficinas,
                               COUNT(DISTINCT e.chamada_id) AS chamadas,
                               COUNT(*) AS esperados,
                               COUNT(*) FILTER (WHERE p.status_presenca = 'FALTA') AS faltas,
                               COUNT(*) FILTER (WHERE p.status_presenca = 'FALTA_JUSTIFICADA') AS justificadas
                          FROM esperados e
                          JOIN adra.turma t ON t.turma_id = e.turma_id
                          LEFT JOIN adra.presenca p
                            ON p.chamada_id = e.chamada_id AND p.assistido_id = e.assistido_id
                         GROUP BY 1, 2, 3
                         ORDER BY 1, 3
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("oficineiroId", oficineiroId)
                .query((rs, i) -> new FrequenciaLinha(
                        rs.getObject("mes", LocalDate.class),
                        rs.getLong("turma_id"),
                        rs.getString("nome_turma"),
                        rs.getString("oficinas"),
                        rs.getLong("chamadas"),
                        rs.getLong("esperados"),
                        rs.getLong("faltas"),
                        rs.getLong("justificadas")))
                .list();
    }

    public record FrequenciaDiaSemana(int diaSemana, long chamadas, long esperados, long ausencias) {
    }

    /** Frequência por dia da semana (1 = segunda … 7 = domingo, ISO), opcionalmente de um grupo. */
    public List<FrequenciaDiaSemana> frequenciaPorDiaDaSemana(LocalDate inicio, LocalDate fim, Long turmaId,
                                                              Long oficineiroId) {
        return jdbc.sql("WITH " + ESPERADOS + """
                        SELECT EXTRACT(ISODOW FROM e.data_chamada)::int AS dia,
                               COUNT(DISTINCT e.chamada_id) AS chamadas,
                               COUNT(*) AS esperados,
                               COUNT(p.assistido_id) AS ausencias
                          FROM esperados e
                          LEFT JOIN adra.presenca p
                            ON p.chamada_id = e.chamada_id AND p.assistido_id = e.assistido_id
                           AND p.status_presenca IN ('FALTA', 'FALTA_JUSTIFICADA')
                         WHERE CAST(:turmaId AS bigint) IS NULL OR e.turma_id = :turmaId
                         GROUP BY 1
                         ORDER BY 1
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("turmaId", turmaId)
                .param("oficineiroId", oficineiroId)
                .query((rs, i) -> new FrequenciaDiaSemana(
                        rs.getInt("dia"), rs.getLong("chamadas"), rs.getLong("esperados"), rs.getLong("ausencias")))
                .list();
    }

    public record FaltasSeguidas(Long assistidoId, String nome, Long turmaId, String turma, long faltas,
                                 LocalDate ultimaChamada) {
    }

    /**
     * Ativos com {@code limiar}+ faltas (não justificadas) seguidas nas chamadas
     * mais recentes — a mesma técnica do Painel (soma acumulada de "quebras"),
     * aqui com o grupo de cada chamada e o escopo do Oficineiro.
     */
    public List<FaltasSeguidas> faltasSeguidas(LocalDate desde, LocalDate hoje, int limiar, Long oficineiroId) {
        return jdbc.sql("""
                        WITH chamadas_do_atendido AS (
                            SELECT DISTINCT ta.assistido_id, ch.chamada_id, ch.data_chamada, ch.turma_id,
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
                             WHERE """ + NO_ESCOPO.formatted("ta.turma_id") + """
                        ),
                        com_quebras AS (
                            SELECT *, SUM(CASE WHEN status <> 'FALTA' THEN 1 ELSE 0 END)
                                          OVER (PARTITION BY assistido_id ORDER BY ordem) AS quebras
                              FROM chamadas_do_atendido
                        ),
                        sequencias AS (
                            SELECT assistido_id, COUNT(*) AS faltas, MAX(data_chamada) AS ultima,
                                   (ARRAY_AGG(turma_id ORDER BY ordem))[1] AS turma_id
                              FROM com_quebras
                             WHERE quebras = 0
                             GROUP BY assistido_id
                            HAVING COUNT(*) >= :limiar
                        )
                        SELECT q.assistido_id, s.nome_completo, q.turma_id, t.nome_turma, q.faltas, q.ultima
                          FROM sequencias q
                          JOIN adra.assistido s ON s.assistido_id = q.assistido_id
                          LEFT JOIN adra.turma t ON t.turma_id = q.turma_id
                         ORDER BY q.faltas DESC, s.nome_completo
                        """)
                .param("desde", desde)
                .param("hoje", hoje)
                .param("limiar", limiar)
                .param("oficineiroId", oficineiroId)
                .query((rs, i) -> new FaltasSeguidas(
                        rs.getLong("assistido_id"), rs.getString("nome_completo"),
                        (Long) rs.getObject("turma_id"), rs.getString("nome_turma"),
                        rs.getLong("faltas"), rs.getObject("ultima", LocalDate.class)))
                .list();
    }

    public record Contagem(String rotulo, long total) {
    }

    /** Atendido ativo hoje (mesma regra do Painel: entrou até hoje e não saiu). */
    private static final String ATIVO_HOJE = """
            COALESCE(s.data_entrada, s.criado_em::date) <= :hoje
               AND (s.status = 'ATIVO' OR s.data_saida > :hoje)
            """;

    /**
     * Ativos hoje agrupados por uma dimensão. {@code expressao} vem SEMPRE de
     * uma constante do serviço (enum), nunca do usuário.
     */
    public List<Contagem> ativosPor(String expressao, String juncao, LocalDate hoje) {
        return jdbc.sql("SELECT " + expressao + " AS rotulo, COUNT(DISTINCT s.assistido_id) AS total"
                        + " FROM adra.assistido s " + juncao
                        + " WHERE " + ATIVO_HOJE
                        + " GROUP BY 1 ORDER BY total DESC, rotulo NULLS LAST")
                .param("hoje", hoje)
                .query((rs, i) -> new Contagem(rs.getString("rotulo"), rs.getLong("total")))
                .list();
    }

    public long ativosHoje(LocalDate hoje) {
        return jdbc.sql("SELECT COUNT(*) FROM adra.assistido s WHERE " + ATIVO_HOJE)
                .param("hoje", hoje)
                .query(Long.class)
                .single();
    }

    public record MovimentoMes(int mes, long entradas, long saidas) {
    }

    /** Entradas e saídas de atendidos por mês do ano (sem data de entrada, vale a de cadastro). */
    public List<MovimentoMes> movimentoPorMes(LocalDate inicio, LocalDate fim) {
        return jdbc.sql("""
                        WITH eventos AS (
                            SELECT COALESCE(data_entrada, criado_em::date) AS dia, 1 AS entrada, 0 AS saida
                              FROM adra.assistido
                             WHERE COALESCE(data_entrada, criado_em::date) BETWEEN :inicio AND :fim
                            UNION ALL
                            SELECT data_saida, 0, 1
                              FROM adra.assistido
                             WHERE data_saida BETWEEN :inicio AND :fim
                        )
                        SELECT EXTRACT(MONTH FROM dia)::int AS mes, SUM(entrada) AS entradas, SUM(saida) AS saidas
                          FROM eventos
                         GROUP BY 1
                         ORDER BY 1
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .query((rs, i) -> new MovimentoMes(rs.getInt("mes"), rs.getLong("entradas"), rs.getLong("saidas")))
                .list();
    }

    /** Ativos numa data (para "ativos no início e no fim do ano"). */
    public long ativosEm(LocalDate data) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM adra.assistido s
                         WHERE COALESCE(s.data_entrada, s.criado_em::date) <= :data
                           AND (s.status = 'ATIVO' OR s.data_saida > :data)
                        """)
                .param("data", data)
                .query(Long.class)
                .single();
    }

    /** Motivos de saída mais comuns no período (texto livre normalizado: minúsculas, sem espaços nas pontas). */
    public List<Contagem> motivosDeSaida(LocalDate inicio, LocalDate fim, int limite) {
        return jdbc.sql("""
                        SELECT COALESCE(NULLIF(lower(btrim(motivo_saida)), ''), 'não informado') AS rotulo,
                               COUNT(*) AS total
                          FROM adra.assistido
                         WHERE data_saida BETWEEN :inicio AND :fim
                         GROUP BY 1
                         ORDER BY total DESC, rotulo
                         LIMIT :limite
                        """)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("limite", limite)
                .query((rs, i) -> new Contagem(rs.getString("rotulo"), rs.getLong("total")))
                .list();
    }

    public record DisciplinaGrupo(Long turmaId, String nomeTurma, long ocorrencias, long advertencias,
                                  long suspensoes, long totalMesAnterior) {
    }

    /** Registros disciplinares (não arquivados) por grupo, no mês e no anterior. */
    public List<DisciplinaGrupo> disciplinaPorGrupo(LocalDate inicioAnterior, LocalDate inicio, LocalDate fim) {
        return jdbc.sql("""
                        SELECT r.turma_id, COALESCE(t.nome_turma, 'Sem grupo') AS nome_turma,
                               COUNT(*) FILTER (WHERE r.data_registro >= :inicio AND r.nivel = 'OCORRENCIA') AS ocorrencias,
                               COUNT(*) FILTER (WHERE r.data_registro >= :inicio AND r.nivel = 'ADVERTENCIA') AS advertencias,
                               COUNT(*) FILTER (WHERE r.data_registro >= :inicio AND r.nivel = 'SUSPENSAO') AS suspensoes,
                               COUNT(*) FILTER (WHERE r.data_registro < :inicio) AS anterior
                          FROM adra.registro_disciplinar r
                          LEFT JOIN adra.turma t ON t.turma_id = r.turma_id
                         WHERE r.arquivado = false
                           AND r.data_registro BETWEEN :inicioAnterior AND :fim
                         GROUP BY r.turma_id, t.nome_turma
                         ORDER BY 2
                        """)
                .param("inicioAnterior", inicioAnterior)
                .param("inicio", inicio)
                .param("fim", fim)
                .query((rs, i) -> new DisciplinaGrupo(
                        (Long) rs.getObject("turma_id"), rs.getString("nome_turma"),
                        rs.getLong("ocorrencias"), rs.getLong("advertencias"),
                        rs.getLong("suspensoes"), rs.getLong("anterior")))
                .list();
    }

    public record SocialTotais(long ativos, long recebemPrograma, long rendaInformada, long pcd,
                               long necessidadesEspecificas, long problemaSaude, long comNis,
                               long estudaContraturno) {
    }

    /** Totais do perfil social dos ativos hoje — só contagens, nunca o dado de uma pessoa. */
    public SocialTotais socialTotais(LocalDate hoje) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS ativos,
                               COUNT(*) FILTER (WHERE s.recebe_programa_transferencia) AS recebem,
                               COUNT(*) FILTER (WHERE s.renda_familiar IS NOT NULL) AS renda,
                               COUNT(*) FILTER (WHERE s.pcd) AS pcd,
                               COUNT(*) FILTER (WHERE NULLIF(btrim(s.necessidades_especificas), '') IS NOT NULL) AS necessidades,
                               COUNT(*) FILTER (WHERE s.possui_problema_saude) AS saude,
                               COUNT(*) FILTER (WHERE NULLIF(btrim(s.nis), '') IS NOT NULL) AS nis,
                               COUNT(*) FILTER (WHERE s.estuda_contraturno) AS contraturno
                          FROM adra.assistido s
                         WHERE """ + ATIVO_HOJE)
                .param("hoje", hoje)
                .query((rs, i) -> new SocialTotais(
                        rs.getLong("ativos"), rs.getLong("recebem"), rs.getLong("renda"), rs.getLong("pcd"),
                        rs.getLong("necessidades"), rs.getLong("saude"), rs.getLong("nis"), rs.getLong("contraturno")))
                .single();
    }

    /** Ativos hoje por programa de transferência de renda (um atendido pode ter vários). */
    public List<Contagem> programasTransferencia(LocalDate hoje) {
        return jdbc.sql("""
                        SELECT apt.programa AS rotulo, COUNT(DISTINCT s.assistido_id) AS total
                          FROM adra.assistido s
                          JOIN adra.assistido_programa_transferencia apt ON apt.assistido_id = s.assistido_id
                         WHERE """ + ATIVO_HOJE + """
                         GROUP BY 1
                         ORDER BY total DESC, rotulo
                        """)
                .param("hoje", hoje)
                .query((rs, i) -> new Contagem(rs.getString("rotulo"), rs.getLong("total")))
                .list();
    }

    /** Ativos hoje por faixa de renda familiar (em reais), na ordem das faixas. */
    public List<Contagem> faixasDeRenda(LocalDate hoje) {
        return jdbc.sql("""
                        SELECT CASE
                                 WHEN s.renda_familiar <= 1000 THEN '1|Até R$ 1.000'
                                 WHEN s.renda_familiar <= 2000 THEN '2|De R$ 1.000,01 a R$ 2.000'
                                 WHEN s.renda_familiar <= 3000 THEN '3|De R$ 2.000,01 a R$ 3.000'
                                 ELSE '4|Acima de R$ 3.000'
                               END AS rotulo,
                               COUNT(*) AS total
                          FROM adra.assistido s
                         WHERE s.renda_familiar IS NOT NULL AND """ + ATIVO_HOJE + """
                         GROUP BY 1
                         ORDER BY 1
                        """)
                .param("hoje", hoje)
                .query((rs, i) -> new Contagem(rs.getString("rotulo").substring(2), rs.getLong("total")))
                .list();
    }

    public record AtendidoGrupo(Long assistidoId, String nome, String grupo, LocalDate nascimento) {
    }

    /** Sem acento e em minúsculas, igual ao termo que o serviço prepara. */
    private static final String NOME_SEM_ACENTO =
            "translate(lower(s.nome_completo), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn')";

    /** Ativos cujo nome contém o termo (palavras separadas por %), no escopo do grupo atual. */
    public List<AtendidoGrupo> buscarAtivosPorNome(String termo, int limite, Long oficineiroId) {
        return jdbc.sql("""
                        SELECT s.assistido_id, s.nome_completo, t.nome_turma, s.data_nascimento
                          FROM adra.assistido s
                          LEFT JOIN adra.turma t ON t.turma_id = s.turma_id
                         WHERE s.status = 'ATIVO'
                           AND """ + NOME_SEM_ACENTO + """
                         LIKE :termo
                           AND (CAST(:oficineiroId AS bigint) IS NULL OR """ + NO_ESCOPO.formatted("s.turma_id") + """
                           )
                         ORDER BY s.nome_completo
                         LIMIT :limite
                        """)
                .param("termo", termo)
                .param("limite", limite)
                .param("oficineiroId", oficineiroId)
                .query((rs, i) -> new AtendidoGrupo(rs.getLong("assistido_id"), rs.getString("nome_completo"),
                        rs.getString("nome_turma"), rs.getObject("data_nascimento", LocalDate.class)))
                .list();
    }

    /** Ativos que fazem aniversário no mês (1–12), no escopo do grupo atual, pelo dia. */
    public List<AtendidoGrupo> aniversariantesDoMes(int mes, Long oficineiroId) {
        return jdbc.sql("""
                        SELECT s.assistido_id, s.nome_completo, t.nome_turma, s.data_nascimento
                          FROM adra.assistido s
                          LEFT JOIN adra.turma t ON t.turma_id = s.turma_id
                         WHERE s.status = 'ATIVO'
                           AND EXTRACT(MONTH FROM s.data_nascimento) = :mes
                           AND (CAST(:oficineiroId AS bigint) IS NULL OR """ + NO_ESCOPO.formatted("s.turma_id") + """
                           )
                         ORDER BY EXTRACT(DAY FROM s.data_nascimento), s.nome_completo
                        """)
                .param("mes", mes)
                .param("oficineiroId", oficineiroId)
                .query((rs, i) -> new AtendidoGrupo(rs.getLong("assistido_id"), rs.getString("nome_completo"),
                        rs.getString("nome_turma"), rs.getObject("data_nascimento", LocalDate.class)))
                .list();
    }
}
