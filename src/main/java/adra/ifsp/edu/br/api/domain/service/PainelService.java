package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.painel.PainelHojeDTO;
import adra.ifsp.edu.br.api.domain.dto.painel.PainelMensalDTO;
import adra.ifsp.edu.br.api.domain.repository.PainelRepository;
import adra.ifsp.edu.br.api.domain.repository.PainelRepository.FrequenciaTurmaMes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FT-05 — Painel do Coordenador. Monta os DTOs a partir das agregações do
 * {@link PainelRepository}: 4 consultas para o mês ({@link #mensal}) e 4 +
 * as pendências da FT-04 para o bloco de hoje ({@link #hoje}). Aqui só há
 * contas sobre dezenas de linhas já somadas no banco.
 */
@Service
@RequiredArgsConstructor
public class PainelService {

    /** Meses na tendência de frequência (inclui o mês escolhido). */
    static final int MESES_TENDENCIA = 6;
    /** Itens por lista no bloco "Precisa de atenção" — o total vem ao lado. */
    static final int LIMITE_LISTA = 6;
    /** Até quantos dias para trás uma chamada esquecida ainda aparece como pendente. */
    static final int DIAS_PENDENCIA = 60;
    /** Janela de aulas para detectar faltas seguidas. */
    static final int DIAS_FALTAS_SEGUIDAS = 45;
    static final int DIAS_ANIVERSARIO = 7;
    static final int LIMITE_ATIVIDADES = 8;

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MES_DIA = DateTimeFormatter.ofPattern("MM-dd");

    private final PainelRepository repository;
    private final DisciplinarService disciplinarService;

    @Transactional(readOnly = true)
    public PainelMensalDTO mensal(YearMonth mes, Long oficinaId) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();
        // Mês corrente: "ativos" e alunos por turma valem para hoje, não para o dia 31.
        LocalDate referencia = fim.isAfter(hoje) ? hoje : fim;
        YearMonth anterior = mes.minusMonths(1);

        // 1 consulta para 6 meses: mês atual, anterior, tendência e barras por turma.
        List<FrequenciaTurmaMes> frequencias = repository.frequenciaPorTurmaEMes(
                mes.minusMonths(MESES_TENDENCIA - 1L).atDay(1), fim, oficinaId);
        Map<YearMonth, List<FrequenciaTurmaMes>> porMes = frequencias.stream()
                .collect(Collectors.groupingBy(f -> YearMonth.from(f.mes())));
        Map<Long, FrequenciaTurmaMes> porTurmaNoMes = porMes.getOrDefault(mes, List.of()).stream()
                .collect(Collectors.toMap(FrequenciaTurmaMes::turmaId, Function.identity()));

        List<PainelRepository.TurmaResumo> turmasResumo = repository.turmas(inicio, fim, referencia, hoje, oficinaId);
        PainelRepository.AssistidosResumo assistidos = repository.assistidos(inicio, fim, referencia, anterior.atEndOfMonth(), oficinaId);
        List<PainelRepository.DisciplinaNivel> disciplina = repository.disciplinaPorNivel(anterior.atDay(1), inicio, fim, oficinaId);
        PainelRepository.OficinasResumo oficinas = repository.oficinas(inicio, fim);

        List<FrequenciaTurmaMes> doMes = porMes.getOrDefault(mes, List.of());
        long faltas = doMes.stream().mapToLong(FrequenciaTurmaMes::faltas).sum();
        long justificadas = doMes.stream().mapToLong(FrequenciaTurmaMes::justificadas).sum();

        List<PainelMensalDTO.Turma> turmas = turmasResumo.stream()
                .map(t -> {
                    FrequenciaTurmaMes f = porTurmaNoMes.get(t.turmaId());
                    return new PainelMensalDTO.Turma(t.turmaId(), t.nomeTurma(), t.nomeOficina(), t.alunos(),
                            t.capacidade(), t.aulasRealizadas(), t.chamadasPendentes(), f == null ? null : percentual(f));
                })
                .sorted(Comparator.comparing(PainelMensalDTO.Turma::frequencia, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PainelMensalDTO.Turma::nomeTurma, String.CASE_INSENSITIVE_ORDER))
                .toList();

        long alunosEmTurmas = turmasResumo.stream().mapToLong(PainelRepository.TurmaResumo::alunos).sum();
        long vagas = turmasResumo.stream()
                .filter(t -> t.capacidade() != null && t.capacidade() > 0)
                .mapToLong(PainelRepository.TurmaResumo::capacidade).sum();
        long alunosEmTurmasComCapacidade = turmasResumo.stream()
                .filter(t -> t.capacidade() != null && t.capacidade() > 0)
                .mapToLong(PainelRepository.TurmaResumo::alunos).sum();

        List<PainelMensalDTO.FrequenciaMes> tendencia = new ArrayList<>();
        for (int i = MESES_TENDENCIA - 1; i >= 0; i--) {
            YearMonth m = mes.minusMonths(i);
            List<FrequenciaTurmaMes> linhas = porMes.getOrDefault(m, List.of());
            tendencia.add(new PainelMensalDTO.FrequenciaMes(m.format(MES), percentual(linhas),
                    linhas.stream().mapToLong(FrequenciaTurmaMes::aulas).sum()));
        }

        return new PainelMensalDTO(
                mes.format(MES),
                LocalDateTime.now(),
                new PainelMensalDTO.Assistidos(assistidos.ativos(), assistidos.ativosAntes(),
                        assistidos.entradas(), assistidos.saidas()),
                new PainelMensalDTO.Oficinas(oficinas.ativas(), oficinas.novas()),
                new PainelMensalDTO.Frequencia(
                        percentual(doMes),
                        percentual(porMes.getOrDefault(anterior, List.of())),
                        turmasResumo.stream().mapToLong(PainelRepository.TurmaResumo::aulasRealizadas).sum(),
                        turmasResumo.stream().mapToLong(PainelRepository.TurmaResumo::chamadasPendentes).sum(),
                        faltas,
                        justificadas),
                new PainelMensalDTO.Ocupacao(turmasResumo.size(), alunosEmTurmas, vagas,
                        vagas == 0 ? null : (int) Math.round(alunosEmTurmasComCapacidade * 100.0 / vagas)),
                disciplina(disciplina),
                tendencia,
                turmas,
                List.of(
                        new PainelMensalDTO.FaixaEtaria("Até 5", assistidos.ate5()),
                        new PainelMensalDTO.FaixaEtaria("6–9", assistidos.de6a9()),
                        new PainelMensalDTO.FaixaEtaria("10–13", assistidos.de10a13()),
                        new PainelMensalDTO.FaixaEtaria("14–17", assistidos.de14a17()),
                        new PainelMensalDTO.FaixaEtaria("18+", assistidos.de18()),
                        new PainelMensalDTO.FaixaEtaria("Sem data", assistidos.semData())));
    }

    @Transactional(readOnly = true)
    public PainelHojeDTO hoje() {
        LocalDate hoje = LocalDate.now();

        List<PainelRepository.FaltasSeguidas> faltas = repository.faltasSeguidas(
                hoje.minusDays(DIAS_FALTAS_SEGUIDAS), hoje, IndicadorFrequencia.LIMIAR_FALTAS_CONSECUTIVAS);
        List<PainelRepository.AulaPendente> pendentes = repository.chamadasPendentes(
                hoje.minusDays(DIAS_PENDENCIA), hoje, LIMITE_LISTA);
        List<PainelRepository.UmPasso> umPasso = repository.umPassoDaProgressao();

        List<PainelHojeDTO.Aniversariante> aniversariantes = repository.aniversariantes(diasDeAniversario(hoje)).stream()
                .map(a -> {
                    LocalDate dia = proximoAniversario(a.nascimento(), hoje);
                    return new PainelHojeDTO.Aniversariante(a.assistidoId(), a.nome(), a.turma(), dia,
                            dia.getYear() - a.nascimento().getYear());
                })
                .sorted(Comparator.comparing(PainelHojeDTO.Aniversariante::dia)
                        .thenComparing(PainelHojeDTO.Aniversariante::nome))
                .toList();

        return new PainelHojeDTO(
                hoje,
                new PainelHojeDTO.Lista<>(faltas.stream().limit(LIMITE_LISTA)
                        .map(f -> new PainelHojeDTO.AlunoFaltas(f.assistidoId(), f.nome(), f.turma(), f.faltas(), f.ultimaAula()))
                        .toList(), faltas.size()),
                new PainelHojeDTO.Lista<>(pendentes.stream()
                        .map(p -> new PainelHojeDTO.ChamadaPendente(p.aulaId(), p.data(), p.horarioInicio(), p.turma()))
                        .toList(), pendentes.isEmpty() ? 0 : pendentes.getFirst().total()),
                disciplinarService.buscarPendencias(),
                new PainelHojeDTO.Lista<>(umPasso.stream().limit(LIMITE_LISTA)
                        .map(u -> new PainelHojeDTO.UmPasso(u.assistidoId(), u.nome(), u.nivel(), u.registros()))
                        .toList(), umPasso.size()),
                aniversariantes,
                repository.atividadesRecentes(LIMITE_ATIVIDADES).stream()
                        .map(a -> new PainelHojeDTO.Atividade(a.tipo(), a.referenciaId(), a.assistidoId(),
                                a.titulo(), a.detalhe(), a.quando()))
                        .toList());
    }

    /** % de presença sobre aluno-aula esperado; `null` sem aula realizada. */
    private static Integer percentual(FrequenciaTurmaMes f) {
        return percentual(List.of(f));
    }

    private static Integer percentual(List<FrequenciaTurmaMes> linhas) {
        long esperados = linhas.stream().mapToLong(FrequenciaTurmaMes::esperados).sum();
        if (esperados == 0) return null;
        long ausentes = linhas.stream().mapToLong(f -> f.faltas() + f.justificadas()).sum();
        return (int) Math.round((esperados - ausentes) * 100.0 / esperados);
    }

    private static PainelMensalDTO.Disciplina disciplina(List<PainelRepository.DisciplinaNivel> niveis) {
        Map<String, PainelRepository.DisciplinaNivel> porNivel = niveis.stream()
                .collect(Collectors.toMap(PainelRepository.DisciplinaNivel::nivel, Function.identity()));
        Function<String, Long> noMes = n -> porNivel.containsKey(n) ? porNivel.get(n).noMes() : 0L;
        return new PainelMensalDTO.Disciplina(
                noMes.apply("OCORRENCIA"),
                noMes.apply("ADVERTENCIA"),
                noMes.apply("SUSPENSAO"),
                noMes.apply("UNIFORME"),
                niveis.stream().mapToLong(PainelRepository.DisciplinaNivel::noMesAnterior).sum());
    }

    /** MM-dd dos próximos dias; quem nasceu em 29/02 comemora em 28/02 nos anos não bissextos. */
    static Set<String> diasDeAniversario(LocalDate hoje) {
        Set<String> dias = new LinkedHashSet<>();
        for (int i = 0; i < DIAS_ANIVERSARIO; i++) {
            LocalDate dia = hoje.plusDays(i);
            dias.add(dia.format(MES_DIA));
            if (dia.getMonthValue() == 2 && dia.getDayOfMonth() == 28 && !dia.isLeapYear()) {
                dias.add("02-29");
            }
        }
        return dias;
    }

    static LocalDate proximoAniversario(LocalDate nascimento, LocalDate hoje) {
        MonthDay md = MonthDay.from(nascimento);
        LocalDate dia = md.isValidYear(hoje.getYear()) ? md.atYear(hoje.getYear()) : LocalDate.of(hoje.getYear(), 2, 28);
        if (dia.isBefore(hoje)) {
            int ano = hoje.getYear() + 1;
            dia = md.isValidYear(ano) ? md.atYear(ano) : LocalDate.of(ano, 2, 28);
        }
        return dia;
    }
}
