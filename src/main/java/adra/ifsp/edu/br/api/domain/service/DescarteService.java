package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.ApiApplication;
import adra.ifsp.edu.br.api.domain.dto.guarda.DescarteDTO;
import adra.ifsp.edu.br.api.domain.dto.guarda.PreviaDescarteDTO;
import adra.ifsp.edu.br.api.domain.dto.guarda.PreviaDescarteDTO.AtendidoRemovidoDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.model.DescarteDados;
import adra.ifsp.edu.br.api.domain.repository.DescarteDadosRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

/**
 * Descarte pelo prazo de guarda: os dados de cada matrícula saem 5 anos depois
 * de ela ser criada; a ficha (e o que só existe por ela) sai quando não sobra
 * matrícula dentro do prazo. Pré-cadastro que nunca virou atendido sai 5 anos
 * depois do envio. Responsável só sai junto com o último atendido dele.
 *
 * <p>Tudo numa transação, em SQL por conjunto (sem uma consulta por atendido) e
 * na ordem das chaves estrangeiras, sem depender de cascata. Os binários saem do
 * Storage depois do commit; o que falhar vai para a fila de remoção.
 * Matrícula ATIVA nunca é descartada.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DescarteService {

    public static final int ANOS_DE_GUARDA = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final DescarteDadosRepository descarteRepository;
    private final RemocaoNoArmazenamento remocao;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final AuditoriaService auditoriaService;

    /** O que está fora do prazo hoje. */
    private record Alvo(LocalDate limite, List<Long> matriculas, List<long[]> anosPorAtendido, List<Long> atendidos,
                        List<Long> preCadastros, List<Long> registros, List<Long> responsaveis, List<Arquivo> arquivos) {
        long bytes() {
            return arquivos.stream().mapToLong(Arquivo::tamanho).sum();
        }
    }

    private record Arquivo(long id, String chave, ProvedorArmazenamento provedor, long tamanho) {
    }

    @Transactional(readOnly = true)
    public PreviaDescarteDTO previa() {
        Alvo alvo = calcular(LocalDate.now());
        List<Integer> anos = alvo.anosPorAtendido().stream().map(p -> (int) p[1]).distinct().sorted().toList();
        List<AtendidoRemovidoDTO> quemSai = alvo.atendidos().isEmpty() ? List.of() : jdbc.query("""
                        select a.assistido_id, a.nome_completo, (select max(ano) from adra.matricula m where m.assistido_id = a.assistido_id) as ultimo
                          from adra.assistido a where a.assistido_id in (:ids) order by a.nome_completo""",
                Map.of("ids", alvo.atendidos()),
                (rs, i) -> new AtendidoRemovidoDTO(rs.getLong(1), rs.getString(2), (Integer) rs.getObject(3, Integer.class)));
        return new PreviaDescarteDTO(alvo.limite(), alvo.matriculas().size(), anos, alvo.atendidos().size(), quemSai,
                alvo.responsaveis().size(), alvo.preCadastros().size(), alvo.registros().size(),
                alvo.arquivos().size(), alvo.bytes());
    }

    public DescarteDTO descartar() {
        Alvo alvo = calcular(LocalDate.now());
        if (alvo.matriculas().isEmpty() && alvo.preCadastros().isEmpty()) {
            throw new RegraNegocioException("Não há dados fora do prazo de guarda.");
        }

        // 1. Registros disciplinares (e o vínculo com o documento assinado).
        if (!alvo.registros().isEmpty()) {
            atualizar("update adra.registro_disciplinar set registro_origem_id = null where registro_origem_id in (:ids)", alvo.registros());
            atualizar("delete from adra.registro_disciplinar where registro_id in (:ids)", alvo.registros());
        }
        // 2. Linhas dos arquivos (o binário sai depois do commit).
        List<Long> idsArquivos = alvo.arquivos().stream().map(Arquivo::id).toList();
        if (!idsArquivos.isEmpty()) {
            atualizar("delete from adra.arquivo where arquivo_id in (:ids)", idsArquivos);
        }
        // 3. Presenças do ano de cada matrícula vencida (de quem continua atendido).
        for (long[] par : alvo.anosPorAtendido()) {
            if (alvo.atendidos().contains(par[0])) {
                continue; // sai inteiro no passo 7
            }
            MapSqlParameterSource p = new MapSqlParameterSource("assistido", par[0])
                    .addValue("inicio", LocalDate.of((int) par[1], 1, 1))
                    .addValue("fim", LocalDate.of((int) par[1], 12, 31));
            String presencasDoAno = """
                    select p.presenca_id from adra.presenca p join adra.chamada c on c.chamada_id = p.chamada_id
                     where p.assistido_id = :assistido and c.data_chamada between :inicio and :fim""";
            jdbc.update("delete from adra.falta_justificada where presenca_id in (" + presencasDoAno + ")", p);
            jdbc.update("delete from adra.presenca where presenca_id in (" + presencasDoAno + ")", p);
        }
        // 4. Matrículas vencidas (com os vínculos de grupo delas) e os envios que as geraram.
        if (!alvo.matriculas().isEmpty()) {
            atualizar("update adra.assistido set matricula_atual_id = null where matricula_atual_id in (:ids)", alvo.matriculas());
            atualizar("delete from adra.turma_aluno where matricula_id in (:ids)", alvo.matriculas());
            atualizar("delete from adra.matricula where matricula_id in (:ids)", alvo.matriculas());
        }
        if (!alvo.preCadastros().isEmpty()) {
            atualizar("delete from adra.pre_cadastro where pre_cadastro_id in (:ids)", alvo.preCadastros());
        }
        // 5. Ficha de quem não tem mais matrícula no prazo, com tudo que só existe por ela.
        if (!alvo.atendidos().isEmpty()) {
            List<Long> ids = alvo.atendidos();
            atualizar("delete from adra.falta_justificada where presenca_id in (select presenca_id from adra.presenca where assistido_id in (:ids))", ids);
            atualizar("delete from adra.presenca where assistido_id in (:ids)", ids);
            atualizar("delete from adra.turma_aluno where assistido_id in (:ids)", ids);
            atualizar("delete from adra.assistido_responsavel where assistido_id in (:ids)", ids);
            atualizar("delete from adra.assistido_programa_transferencia where assistido_id in (:ids)", ids);
            atualizar("delete from adra.assistido_turma_historico where assistido_id in (:ids)", ids);
            atualizar("delete from adra.solicitacao_desligamento where assistido_id in (:ids)", ids);
            atualizar("delete from adra.assistido where assistido_id in (:ids)", ids);
        }
        // 6. Responsáveis que ficaram sem nenhum atendido.
        if (!alvo.responsaveis().isEmpty()) {
            atualizar("delete from adra.responsavel where responsavel_id in (:ids)", alvo.responsaveis());
        }
        // 7. Auditoria: o histórico das ações fica, os dados pessoais dele não.
        anonimizarAuditoria(alvo, idsArquivos);

        DescarteDados registro = descarteRepository.save(new DescarteDados(usuarioAutenticadoService.getUsuarioAtual(),
                alvo.limite(), alvo.matriculas().size(), alvo.atendidos().size(), alvo.responsaveis().size(),
                alvo.preCadastros().size(), alvo.arquivos().size(), alvo.bytes()));
        auditoriaService.registrar(ModuloSistema.ASSISTIDOS, "descarte_dados", registro.getDescarteId(),
                AcaoSistema.EXCLUIR, null,
                Map.of("matriculas", alvo.matriculas().size(), "atendidos", alvo.atendidos().size(),
                        "preCadastros", alvo.preCadastros().size(), "arquivos", alvo.arquivos().size()),
                "Descarte pelo prazo de guarda (" + ANOS_DE_GUARDA + " anos)");

        remocao.removerAposCommit(alvo.arquivos().stream()
                .map(a -> new RemocaoNoArmazenamento.Chave(a.provedor(), a.chave())).toList());
        return DescarteDTO.de(registro);
    }

    @Transactional(readOnly = true)
    public List<DescarteDTO> historico() {
        return descarteRepository.findTop20ByOrderByExecutadoEmDesc().stream().map(DescarteDTO::de).toList();
    }

    // ---------------------------------------------------------------

    private Alvo calcular(LocalDate hoje) {
        LocalDate limite = hoje.minusYears(ANOS_DE_GUARDA);
        OffsetDateTime corte = limite.atStartOfDay(ApiApplication.FUSO).toOffsetDateTime();
        MapSqlParameterSource p = new MapSqlParameterSource("corte", corte);

        List<long[]> vencidas = jdbc.query("""
                select matricula_id, assistido_id, ano from adra.matricula
                 where criado_em < :corte and status <> 'ATIVA'""", p,
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getInt(3)});
        List<Long> matriculas = vencidas.stream().map(v -> v[0]).toList();
        List<long[]> anosPorAtendido = vencidas.stream().map(v -> new long[] {v[1], v[2]}).toList();

        // Sai por inteiro quem não tem nenhuma matrícula dentro do prazo (nem ativa).
        List<Long> atendidos = jdbc.queryForList("""
                select distinct m.assistido_id from adra.matricula m
                 where m.criado_em < :corte and m.status <> 'ATIVA'
                   and not exists (select 1 from adra.matricula f where f.assistido_id = m.assistido_id
                                    and (f.criado_em >= :corte or f.status = 'ATIVA'))""", p, Long.class);

        Set<Long> preCadastros = new LinkedHashSet<>(jdbc.queryForList("""
                select pre_cadastro_id from adra.pre_cadastro
                 where criado_em < :corte and status <> 'CONVERTIDO'""", p, Long.class));
        if (!matriculas.isEmpty()) {
            preCadastros.addAll(jdbc.queryForList(
                    "select pre_cadastro_id from adra.matricula where matricula_id in (:ids) and pre_cadastro_id is not null",
                    Map.of("ids", matriculas), Long.class));
        }
        if (!atendidos.isEmpty()) {
            preCadastros.addAll(jdbc.queryForList("select pre_cadastro_id from adra.pre_cadastro where assistido_id in (:ids)",
                    Map.of("ids", atendidos), Long.class));
        }

        Set<Long> registros = new LinkedHashSet<>();
        if (!atendidos.isEmpty()) {
            registros.addAll(jdbc.queryForList("select registro_id from adra.registro_disciplinar where assistido_id in (:ids)",
                    Map.of("ids", atendidos), Long.class));
        }
        for (long[] par : anosPorAtendido) {
            registros.addAll(jdbc.queryForList("""
                    select registro_id from adra.registro_disciplinar
                     where assistido_id = :assistido and data_registro between :inicio and :fim""",
                    new MapSqlParameterSource("assistido", par[0])
                            .addValue("inicio", LocalDate.of((int) par[1], 1, 1))
                            .addValue("fim", LocalDate.of((int) par[1], 12, 31)), Long.class));
        }
        // Registros que sobram e apontam para um que sai (encadeamento) perdem só a referência.

        List<Long> responsaveis = atendidos.isEmpty() ? List.of() : jdbc.queryForList("""
                select distinct ar.responsavel_id from adra.assistido_responsavel ar
                 where ar.assistido_id in (:ids)
                   and not exists (select 1 from adra.assistido_responsavel outro
                                    where outro.responsavel_id = ar.responsavel_id and outro.assistido_id not in (:ids))""",
                Map.of("ids", atendidos), Long.class);

        List<Arquivo> arquivos = arquivos(matriculas, atendidos, List.copyOf(preCadastros), responsaveis, List.copyOf(registros));
        return new Alvo(limite, matriculas, anosPorAtendido, atendidos, List.copyOf(preCadastros),
                List.copyOf(registros), responsaveis, arquivos);
    }

    private List<Arquivo> arquivos(List<Long> matriculas, List<Long> atendidos, List<Long> preCadastros,
                                   List<Long> responsaveis, List<Long> registros) {
        List<String> condicoes = new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (!matriculas.isEmpty()) {
            condicoes.add("matricula_id in (:matriculas)");
            p.addValue("matriculas", matriculas);
        }
        if (!atendidos.isEmpty()) {
            condicoes.add("(entidade_tipo = 'ASSISTIDO' and entidade_id in (:atendidos))");
            p.addValue("atendidos", atendidos);
        }
        if (!preCadastros.isEmpty()) {
            condicoes.add("(entidade_tipo = 'PRE_CADASTRO' and entidade_id in (:preCadastros))");
            p.addValue("preCadastros", preCadastros);
        }
        if (!responsaveis.isEmpty()) {
            condicoes.add("(entidade_tipo = 'RESPONSAVEL' and entidade_id in (:responsaveis))");
            p.addValue("responsaveis", responsaveis);
        }
        if (!registros.isEmpty()) {
            condicoes.add("arquivo_id in (select documento_arquivo_id from adra.registro_disciplinar where registro_id in (:registros))");
            p.addValue("registros", registros);
        }
        if (condicoes.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select arquivo_id, chave, provedor, tamanho_bytes from adra.arquivo where "
                        + String.join(" or ", condicoes), p,
                (rs, i) -> new Arquivo(rs.getLong(1), rs.getString(2), ProvedorArmazenamento.valueOf(rs.getString(3)),
                        rs.getLong(4)));
    }

    private void anonimizarAuditoria(Alvo alvo, List<Long> arquivos) {
        anonimizar("assistido", alvo.atendidos());
        anonimizar("pre_cadastro", alvo.preCadastros());
        anonimizar("responsavel", alvo.responsaveis());
        anonimizar("registro_disciplinar", alvo.registros());
        anonimizar("arquivo", arquivos);
    }

    private void anonimizar(String entidade, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update("""
                update adra.log_auditoria
                   set valor_anterior = null, valor_novo = null, observacao = 'Dados descartados pelo prazo de guarda'
                 where entidade_afetada = :entidade and entidade_id in (:ids)""",
                new MapSqlParameterSource("entidade", entidade).addValue("ids", ids));
    }

    private void atualizar(String sql, Collection<Long> ids) {
        jdbc.update(sql, Map.of("ids", ids));
    }
}
