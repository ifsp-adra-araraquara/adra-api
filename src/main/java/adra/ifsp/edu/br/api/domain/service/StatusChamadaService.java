package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.chamada.StatusChamadaDiaDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusChamada;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Situação das chamadas num período, grupo a grupo e dia a dia (relatório por
 * período, aba Pendentes e painel). Pendente = dia de chamada de um grupo ativo,
 * até hoje, que não é feriado nem dia sem atividade e não tem chamada lançada.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatusChamadaService {

    private final ChamadaRepository chamadaRepository;
    private final TurmaRepository turmaRepository;
    private final DiasDeChamadaService diasDeChamadaService;

    /** Lançadas e pendentes do período (inclusive), da mais recente para a mais antiga. */
    public List<StatusChamadaDiaDTO> noPeriodo(LocalDate inicio, LocalDate fim) {
        List<Chamada> lancadas = chamadaRepository.findByDataChamadaBetween(inicio, fim);
        List<StatusChamadaDiaDTO> status = new ArrayList<>(lancadas.stream().map(StatusChamadaService::lancada).toList());
        status.addAll(pendentes(inicio, fim, lancadas));
        status.sort(Comparator.comparing(StatusChamadaDiaDTO::data).reversed()
                .thenComparing(StatusChamadaDiaDTO::nomeTurma, String.CASE_INSENSITIVE_ORDER));
        return status;
    }

    /** Só as pendentes do período, da mais recente para a mais antiga. */
    public List<StatusChamadaDiaDTO> pendentes(LocalDate inicio, LocalDate fim) {
        return noPeriodo(inicio, fim).stream()
                .filter(s -> s.status() == StatusChamada.PENDENTE)
                .toList();
    }

    private List<StatusChamadaDiaDTO> pendentes(LocalDate inicio, LocalDate fim, List<Chamada> lancadas) {
        LocalDate ultimoDia = fim.isAfter(LocalDate.now()) ? LocalDate.now() : fim;
        Map<LocalDate, String> semChamada = diasDeChamadaService.diasSemChamada(inicio, ultimoDia);
        Set<String> jaLancadas = lancadas.stream()
                .map(c -> chave(c.getTurma().getTurmaId(), c.getDataChamada()))
                .collect(Collectors.toSet());

        List<StatusChamadaDiaDTO> pendentes = new ArrayList<>();
        for (Turma turma : turmaRepository.findByAtivoTrue()) {
            for (LocalDate dia = primeiroDia(turma, inicio); !dia.isAfter(ultimoDia); dia = dia.plusDays(1)) {
                boolean esperada = diasDeChamadaService.temChamadaNoDia(turma, dia) && !semChamada.containsKey(dia);
                if (esperada && !jaLancadas.contains(chave(turma.getTurmaId(), dia))) {
                    pendentes.add(new StatusChamadaDiaDTO(turma.getTurmaId(), turma.getNomeTurma(), dia, StatusChamada.PENDENTE, null));
                }
            }
        }
        return pendentes;
    }

    /** Grupo criado no meio do período só cobra chamada a partir do dia em que passou a existir. */
    private static LocalDate primeiroDia(Turma turma, LocalDate inicio) {
        if (turma.getCriadoEm() == null) {
            return inicio;
        }
        LocalDate criacao = turma.getCriadoEm().toLocalDate();
        return criacao.isAfter(inicio) ? criacao : inicio;
    }

    private static StatusChamadaDiaDTO lancada(Chamada chamada) {
        return new StatusChamadaDiaDTO(
                chamada.getTurma().getTurmaId(),
                chamada.getTurma().getNomeTurma(),
                chamada.getDataChamada(),
                StatusChamada.LANCADA,
                chamada.getCriadoPor().getNomeCompleto());
    }

    private static String chave(Long turmaId, LocalDate dia) {
        return turmaId + "|" + dia;
    }
}
