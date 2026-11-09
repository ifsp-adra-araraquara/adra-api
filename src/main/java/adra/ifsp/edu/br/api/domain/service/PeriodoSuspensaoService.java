package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * A suspensão conta dias de atividade do grupo do atendido, não dias corridos (Eugênio,
 * reunião de 05/11): "3 dias aplicados na quinta valem sexta, segunda e terça". Fim de
 * semana, feriado e dia sem chamada no calendário não contam; num grupo de segunda a
 * quarta, só esses dias contam. Sem grupo, valem os dias de segunda a sexta.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodoSuspensaoService {

    /** "Suspenso por três dias" (cartaz "Disciplina ADRA"). */
    public static final int DIAS_DE_ATIVIDADE = 3;
    /** Trava de segurança: um grupo sem nenhum dia de atividade não pode girar para sempre. */
    private static final int MAXIMO_DIAS_PROCURADOS = 120;

    private final TurmaAlunosRepository turmaAlunosRepository;
    private final DiasDeChamadaService diasDeChamadaService;

    /** Período padrão: começa no próximo dia de atividade depois do fato e dura 3 dias de atividade. */
    public PeriodoSuspensaoDTO sugerir(Assistido assistido, LocalDate dataDoFato) {
        LocalDate inicio = proximoDiaDeAtividade(assistido, dataDoFato);
        return periodo(assistido, inicio, fimContandoDe(assistido, inicio));
    }

    /** O último dia quando a suspensão começa em `inicio` e dura 3 dias de atividade. */
    public LocalDate fimContandoDe(Assistido assistido, LocalDate inicio) {
        Turma grupo = grupoAtual(assistido);
        LocalDate dia = ehDiaDeAtividade(grupo, inicio) ? inicio : proximoDiaDeAtividade(grupo, inicio);
        for (int contados = 1; contados < DIAS_DE_ATIVIDADE; contados++) {
            dia = proximoDiaDeAtividade(grupo, dia);
        }
        return dia;
    }

    /** Período já registrado (inclusive com datas ajustadas à mão): quantos dias de atividade e quando volta. */
    public PeriodoSuspensaoDTO periodo(Assistido assistido, LocalDate inicio, LocalDate fim) {
        Turma grupo = grupoAtual(assistido);
        int dias = 0;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            if (ehDiaDeAtividade(grupo, dia)) {
                dias++;
            }
        }
        return new PeriodoSuspensaoDTO(inicio, fim, dias, proximoDiaDeAtividade(grupo, fim));
    }

    private LocalDate proximoDiaDeAtividade(Assistido assistido, LocalDate depoisDe) {
        return proximoDiaDeAtividade(grupoAtual(assistido), depoisDe);
    }

    private LocalDate proximoDiaDeAtividade(Turma grupo, LocalDate depoisDe) {
        LocalDate dia = depoisDe.plusDays(1);
        for (int i = 0; i < MAXIMO_DIAS_PROCURADOS && !ehDiaDeAtividade(grupo, dia); i++) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    private boolean ehDiaDeAtividade(Turma grupo, LocalDate dia) {
        boolean diaDoGrupo = temDiasDefinidos(grupo)
                ? diasDeChamadaService.temChamadaNoDia(grupo, dia)
                : dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY;
        return diaDoGrupo && diasDeChamadaService.motivoSemChamada(dia) == null;
    }

    private static boolean temDiasDefinidos(Turma grupo) {
        return grupo != null && grupo.getDiasDaSemana() != null && !grupo.getDiasDaSemana().isEmpty();
    }

    private Turma grupoAtual(Assistido assistido) {
        return turmaAlunosRepository.findByAssistidoAndStatusAndDataSaidaIsNull(assistido, StatusGeral.ATIVO).stream()
                .findFirst()
                .map(TurmaAlunos::getTurma)
                .orElse(null);
    }
}
