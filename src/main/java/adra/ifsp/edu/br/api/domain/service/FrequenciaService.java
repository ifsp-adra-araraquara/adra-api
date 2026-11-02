package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.presenca.AlunoGradeMensalDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.ColunaGradeDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.FrequenciaDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.GradeMensalTurmaDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.TipoColunaGrade;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leituras de frequência sobre a chamada por grupo e dia (FT-15): a grade
 * mensal do grupo e o histórico de um atendido. Modelo esparso: sem falta
 * registrada na chamada = presente.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FrequenciaService {

    private final ChamadaRepository chamadaRepository;
    private final PresencaRepository presencaRepository;
    private final TurmaRepository turmaRepository;
    private final TurmaAlunosRepository turmaAlunosRepository;
    private final AssistidoRepository assistidoRepository;
    private final DiasDeChamadaService diasDeChamadaService;

    /** Grade grupo × dias do mês, no formato da planilha. Linhas: quem esteve no grupo em algum dia do mês. */
    public GradeMensalTurmaDTO gradeMensal(Long turmaId, YearMonth mes) {
        Turma turma = turmaRepository.findById(turmaId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Grupo não encontrado: id " + turmaId));
        LocalDate inicio = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();

        Map<LocalDate, Chamada> chamadasPorData = chamadaRepository
                .findByTurmaAndDataChamadaBetween(turma, inicio, fim).stream()
                .collect(Collectors.toMap(Chamada::getDataChamada, Function.identity()));
        List<ColunaGradeDTO> colunas = colunas(turma, inicio, fim, chamadasPorData);

        List<TurmaAlunos> vinculos = turmaAlunosRepository.findVinculosNoPeriodo(turma, inicio, fim);
        List<Assistido> atendidos = semRepetir(vinculos);
        Map<Long, Map<Long, StatusPresenca>> faltas = faltasPorAtendidoEChamada(chamadasPorData.values(), atendidos);
        Map<Long, List<TurmaAlunos>> vinculosPorAtendido = vinculos.stream()
                .collect(Collectors.groupingBy(v -> v.getAssistido().getAssistidoId()));

        List<AlunoGradeMensalDTO> linhas = atendidos.stream()
                .map(atendido -> linhaDaGrade(atendido, colunas, chamadasPorData,
                        vinculosPorAtendido.get(atendido.getAssistidoId()),
                        faltas.getOrDefault(atendido.getAssistidoId(), Map.of())))
                .toList();

        return new GradeMensalTurmaDTO(turma.getTurmaId(), turma.getNomeTurma(),
                mes.getYear(), mes.getMonthValue(), colunas, linhas);
    }

    /** Todas as chamadas lançadas dos grupos por onde o atendido passou, no período de cada vínculo. Mais recente primeiro. */
    public List<FrequenciaDiaDTO> historicoDoAtendido(Long assistidoId) {
        Assistido atendido = assistidoRepository.findById(assistidoId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Atendido não encontrado: id " + assistidoId));

        List<FrequenciaDiaDTO> historico = new ArrayList<>();
        for (TurmaAlunos vinculo : turmaAlunosRepository.findByAssistidoOrderByDataEntradaDesc(atendido)) {
            LocalDate fim = vinculo.getDataSaida() != null ? vinculo.getDataSaida() : LocalDate.now();
            List<Chamada> chamadas = chamadaRepository.findByTurmaAndDataChamadaBetween(
                    vinculo.getTurma(), vinculo.getDataEntrada(), fim);
            Map<Long, StatusPresenca> faltas = faltasPorAtendidoEChamada(chamadas, List.of(atendido))
                    .getOrDefault(atendido.getAssistidoId(), Map.of());
            for (Chamada chamada : chamadas) {
                historico.add(new FrequenciaDiaDTO(
                        chamada.getDataChamada(),
                        vinculo.getTurma().getTurmaId(),
                        vinculo.getTurma().getNomeTurma(),
                        faltas.getOrDefault(chamada.getChamadaId(), StatusPresenca.PRESENTE)));
            }
        }
        historico.sort(Comparator.comparing(FrequenciaDiaDTO::data).reversed());
        return historico;
    }

    /** Uma coluna por dia de chamada do grupo no mês; dia com chamada lançada fora da grade também entra. */
    private List<ColunaGradeDTO> colunas(Turma turma, LocalDate inicio, LocalDate fim, Map<LocalDate, Chamada> chamadasPorData) {
        Map<LocalDate, String> semChamada = diasDeChamadaService.diasSemChamada(inicio, fim);
        LocalDate hoje = LocalDate.now();
        List<ColunaGradeDTO> colunas = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            if (chamadasPorData.containsKey(dia)) {
                colunas.add(new ColunaGradeDTO(dia, TipoColunaGrade.LANCADA, null));
            } else if (diasDeChamadaService.temChamadaNoDia(turma, dia)) {
                colunas.add(colunaSemLancamento(dia, hoje, semChamada.get(dia)));
            }
        }
        return colunas;
    }

    private static ColunaGradeDTO colunaSemLancamento(LocalDate dia, LocalDate hoje, String motivoSemChamada) {
        if (motivoSemChamada != null) {
            return new ColunaGradeDTO(dia, TipoColunaGrade.SEM_CHAMADA, motivoSemChamada);
        }
        return new ColunaGradeDTO(dia, dia.isAfter(hoje) ? TipoColunaGrade.FUTURA : TipoColunaGrade.PENDENTE, null);
    }

    /** `presencas` paralela às colunas: só tem valor nas LANCADA em que o atendido estava no grupo e não desligado. */
    private static AlunoGradeMensalDTO linhaDaGrade(Assistido atendido, List<ColunaGradeDTO> colunas,
                                                    Map<LocalDate, Chamada> chamadasPorData,
                                                    List<TurmaAlunos> vinculos, Map<Long, StatusPresenca> faltas) {
        List<StatusPresenca> presencas = colunas.stream()
                .map(coluna -> {
                    if (coluna.tipo() != TipoColunaGrade.LANCADA) return null;
                    LocalDate data = coluna.data();
                    boolean noGrupo = vinculos.stream().anyMatch(v ->
                            !v.getDataEntrada().isAfter(data) && (v.getDataSaida() == null || !v.getDataSaida().isBefore(data)));
                    if (!noGrupo || !AssistidoSpecification.elegivelParaChamada(atendido, data)) return null;
                    return faltas.getOrDefault(chamadasPorData.get(data).getChamadaId(), StatusPresenca.PRESENTE);
                })
                .collect(Collectors.toCollection(ArrayList::new));

        boolean desligado = atendido.getStatus() != StatusGeral.ATIVO && atendido.getDataSaida() != null;
        return new AlunoGradeMensalDTO(
                atendido.getAssistidoId(),
                atendido.getNomeCompleto(),
                atendido.getDataNascimento(),
                desligado ? atendido.getDataSaida() : null,
                desligado ? atendido.getMotivoSaida() : null,
                presencas);
    }

    private Map<Long, Map<Long, StatusPresenca>> faltasPorAtendidoEChamada(
            Collection<Chamada> chamadas, List<Assistido> atendidos) {
        Map<Long, Map<Long, StatusPresenca>> faltas = new HashMap<>();
        if (chamadas.isEmpty() || atendidos.isEmpty()) {
            return faltas;
        }
        for (Presenca falta : presencaRepository.findByChamadaInAndAssistidoIn(chamadas, atendidos)) {
            faltas.computeIfAbsent(falta.getAssistido().getAssistidoId(), id -> new HashMap<>())
                    .put(falta.getChamada().getChamadaId(), falta.getStatusPresenca());
        }
        return faltas;
    }

    private static List<Assistido> semRepetir(List<TurmaAlunos> vinculos) {
        return vinculos.stream()
                .map(TurmaAlunos::getAssistido)
                .collect(Collectors.toMap(Assistido::getAssistidoId, a -> a, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(Assistido::getNomeCompleto))
                .toList();
    }
}
