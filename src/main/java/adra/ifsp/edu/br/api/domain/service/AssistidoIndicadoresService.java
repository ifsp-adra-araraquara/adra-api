package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.presenca.AutorizadoRetiradaDTO;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.repository.AssistidoResponsavelRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository.FrequenciaDoAssistido;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dados derivados das linhas da listagem de assistidos — responsável principal
 * e indicadores de frequência — calculados em lote. Frequência com a mesma
 * reconstituição de {@link FrequenciaService#historicoDoAtendido}, pra lista e
 * ficha nunca divergirem.
 */
@Service
@RequiredArgsConstructor
public class AssistidoIndicadoresService {

    private final PresencaRepository presencaRepository;
    private final AssistidoResponsavelRepository assistidoResponsavelRepository;

    @Transactional(readOnly = true)
    public Map<Long, IndicadorFrequencia> frequencias(Collection<Assistido> assistidos) {
        if (assistidos.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = assistidos.stream().map(Assistido::getAssistidoId).toList();
        Map<Long, FrequenciaDoAssistido> frequencias = presencaRepository.frequencias(ids, LocalDate.now()).stream()
                .collect(Collectors.toMap(FrequenciaDoAssistido::getAssistidoId, Function.identity()));

        Map<Long, IndicadorFrequencia> resultado = new HashMap<>();
        for (Assistido assistido : assistidos) {
            FrequenciaDoAssistido frequencia = frequencias.get(assistido.getAssistidoId());
            resultado.put(assistido.getAssistidoId(), frequencia == null
                    ? IndicadorFrequencia.SEM_AULAS
                    : IndicadorFrequencia.de(frequencia.getTotal(), frequencia.getPresentes(),
                            frequencia.getFaltasConsecutivas(), assistido.getStatus()));
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
