package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.model.ExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.ExcecaoCalendarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Em quais dias um grupo tem chamada: os dias da semana do grupo (turma_dia_semana),
 * menos feriados nacionais e dias sem atividade do calendário (excecao_calendario).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiasDeChamadaService {

    private static final String FERIADO_NACIONAL = "Feriado nacional";
    private static final String DIA_SEM_CHAMADA = "Dia sem chamada no calendário";

    private final ExcecaoCalendarioRepository excecaoCalendarioRepository;

    public boolean temChamadaNoDia(Turma turma, LocalDate data) {
        return turma.getDiasDaSemana() != null && turma.getDiasDaSemana().contains(data.getDayOfWeek());
    }

    /** Por que o dia não tem chamada (feriado ou calendário); null quando tem. */
    public String motivoSemChamada(LocalDate data) {
        if (FeriadosNacionais.ehFeriado(data)) {
            return FERIADO_NACIONAL;
        }
        return excecaoCalendarioRepository.findByDataExcecao(data)
                .map(DiasDeChamadaService::descricao)
                .orElse(null);
    }

    /** Dias sem chamada no período (inclusive), com o motivo de cada um. */
    public Map<LocalDate, String> diasSemChamada(LocalDate inicio, LocalDate fim) {
        Map<LocalDate, String> dias = new HashMap<>();
        FeriadosNacionais.entre(inicio, fim).forEach(data -> dias.put(data, FERIADO_NACIONAL));
        excecaoCalendarioRepository.findByDataExcecaoBetween(inicio, fim)
                .forEach(excecao -> dias.putIfAbsent(excecao.getDataExcecao(), descricao(excecao)));
        return dias;
    }

    private static String descricao(ExcecaoCalendario excecao) {
        return excecao.getDescricao() != null && !excecao.getDescricao().isBlank()
                ? excecao.getDescricao()
                : DIA_SEM_CHAMADA;
    }
}
