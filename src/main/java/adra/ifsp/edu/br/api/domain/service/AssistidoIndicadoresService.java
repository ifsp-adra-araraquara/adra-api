package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.presenca.AutorizadoRetiradaDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.repository.AssistidoResponsavelRepository;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dados derivados das linhas da listagem de assistidos — responsável principal
 * e indicadores de frequência — calculados em lote (4 queries por página, não
 * 1 por aluno). Frequência com a mesma reconstituição de
 * {@link FrequenciaService#historicoDoAtendido}, pra lista e ficha nunca
 * divergirem.
 */
@Service
@RequiredArgsConstructor
public class AssistidoIndicadoresService {

    private final TurmaAlunosRepository turmaAlunosRepository;
    private final ChamadaRepository chamadaRepository;
    private final PresencaRepository presencaRepository;
    private final AssistidoResponsavelRepository assistidoResponsavelRepository;

    @Transactional(readOnly = true)
    public Map<Long, IndicadorFrequencia> frequencias(Collection<Assistido> assistidos) {
        if (assistidos.isEmpty()) {
            return Map.of();
        }

        List<TurmaAlunos> vinculos = turmaAlunosRepository.findByAssistidoIn(assistidos);
        Set<Turma> turmas = vinculos.stream().map(TurmaAlunos::getTurma).collect(Collectors.toSet());
        List<Chamada> chamadas = turmas.isEmpty() ? List.of() : chamadaRepository.findByTurmaIn(turmas);

        Map<Long, List<Chamada>> chamadasPorTurma = chamadas.stream()
                .collect(Collectors.groupingBy(c -> c.getTurma().getTurmaId()));

        Map<Long, Map<Long, StatusPresenca>> faltasPorAssistido = new HashMap<>();
        if (!chamadas.isEmpty()) {
            for (Presenca falta : presencaRepository.findByChamadaInAndAssistidoIn(chamadas, assistidos)) {
                faltasPorAssistido
                        .computeIfAbsent(falta.getAssistido().getAssistidoId(), k -> new HashMap<>())
                        .put(falta.getChamada().getChamadaId(), falta.getStatusPresenca());
            }
        }

        Map<Long, List<TurmaAlunos>> vinculosPorAssistido = vinculos.stream()
                .collect(Collectors.groupingBy(v -> v.getAssistido().getAssistidoId()));

        Map<Long, IndicadorFrequencia> resultado = new HashMap<>();
        for (Assistido assistido : assistidos) {
            Long id = assistido.getAssistidoId();
            Map<Long, StatusPresenca> faltas = faltasPorAssistido.getOrDefault(id, Map.of());

            // chamadaId -> chamada, só as dos dias em que o atendido estava no grupo
            Map<Long, Chamada> chamadasDoAssistido = new LinkedHashMap<>();
            for (TurmaAlunos vinculo : vinculosPorAssistido.getOrDefault(id, List.of())) {
                LocalDate fim = vinculo.getDataSaida() != null ? vinculo.getDataSaida() : LocalDate.now();
                for (Chamada chamada : chamadasPorTurma.getOrDefault(vinculo.getTurma().getTurmaId(), List.of())) {
                    LocalDate data = chamada.getDataChamada();
                    if (!data.isBefore(vinculo.getDataEntrada()) && !data.isAfter(fim)) {
                        chamadasDoAssistido.putIfAbsent(chamada.getChamadaId(), chamada);
                    }
                }
            }

            List<StatusPresenca> statusMaisRecentePrimeiro = chamadasDoAssistido.values().stream()
                    .sorted(Comparator.comparing(Chamada::getDataChamada).thenComparing(Chamada::getChamadaId).reversed())
                    .map(chamada -> faltas.getOrDefault(chamada.getChamadaId(), StatusPresenca.PRESENTE))
                    .toList();

            resultado.put(id, IndicadorFrequencia.de(statusMaisRecentePrimeiro, assistido.getStatus()));
        }
        return resultado;
    }

    /** Responsável principal de cada assistido; sem principal marcado, o primeiro em ordem alfabética. */
    @Transactional(readOnly = true)
    public Map<Long, AssistidoResponsavel> responsaveisPrincipais(Collection<Assistido> assistidos) {
        if (assistidos.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = assistidos.stream().map(Assistido::getAssistidoId).toList();

        Comparator<AssistidoResponsavel> prioridade = Comparator
                .comparing(AssistidoResponsavel::isResponsavelPrincipal).reversed()
                .thenComparing(ar -> ar.getResponsavel().getNomeCompleto(), String.CASE_INSENSITIVE_ORDER);

        return assistidoResponsavelRepository.findComResponsavelByAssistidoIds(ids).stream()
                .collect(Collectors.groupingBy(
                        ar -> ar.getAssistido().getAssistidoId(),
                        Collectors.collectingAndThen(Collectors.toList(),
                                lista -> lista.stream().min(prioridade).orElseThrow())));
    }

    /** Quem pode buscar cada assistido (responsáveis "autorizado a retirar"), principal primeiro. */
    @Transactional(readOnly = true)
    public Map<Long, List<AutorizadoRetiradaDTO>> autorizadosRetirada(Collection<Assistido> assistidos) {
        if (assistidos.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = assistidos.stream().map(Assistido::getAssistidoId).toList();

        return assistidoResponsavelRepository.findComResponsavelByAssistidoIds(ids).stream()
                .filter(AssistidoResponsavel::isAutorizadoRetirada)
                .sorted(Comparator.comparing(AssistidoResponsavel::isResponsavelPrincipal).reversed()
                        .thenComparing(v -> v.getResponsavel().getNomeCompleto()))
                .collect(Collectors.groupingBy(
                        v -> v.getAssistido().getAssistidoId(),
                        Collectors.mapping(v -> new AutorizadoRetiradaDTO(
                                v.getResponsavel().getNomeCompleto(),
                                v.getParentesco(),
                                v.getResponsavel().getTelefone()), Collectors.toList())));
    }
}
