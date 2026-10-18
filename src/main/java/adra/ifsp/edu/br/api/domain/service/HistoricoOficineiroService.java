package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.historico.OficineiroHistoricoDTO;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.OficinaOficineiroHistorico;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaOficineiroHistorico;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaOficineiroHistoricoRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaOficineiroHistoricoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Mantém o histórico de oficineiros responsáveis por oficina e por turma.
 * Chamado depois de salvar a oficina/turma: se o oficineiro mudou, fecha o
 * período aberto (até ontem) e abre um novo a partir de hoje.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HistoricoOficineiroService {

    private final OficinaOficineiroHistoricoRepository oficinaHistoricoRepository;
    private final TurmaOficineiroHistoricoRepository turmaHistoricoRepository;
    private final AulaRepository aulaRepository;

    public void registrar(Oficina oficina) {
        Usuario atual = oficina.getOficineiroResponsavel();
        var aberto = oficinaHistoricoRepository.findByOficina_OficinaIdAndDataFimIsNull(oficina.getOficinaId());
        if (aberto.isPresent() && mesmoOficineiro(aberto.get().getOficineiro(), atual)) {
            return;
        }
        LocalDate hoje = LocalDate.now();
        aberto.ifPresent(periodo -> {
            periodo.setDataFim(fimDoPeriodo(periodo.getDataInicio(), hoje));
            // grava o fechamento ANTES do insert: o Hibernate faria o INSERT primeiro e
            // bateria no índice único de "um período aberto por oficina".
            oficinaHistoricoRepository.saveAndFlush(periodo);
        });
        if (atual != null) {
            oficinaHistoricoRepository.save(OficinaOficineiroHistorico.builder()
                    .oficina(oficina).oficineiro(atual).dataInicio(hoje).build());
        }
    }

    public void registrar(Turma turma) {
        Usuario atual = turma.getOficineiroResponsavel();
        var aberto = turmaHistoricoRepository.findByTurma_TurmaIdAndDataFimIsNull(turma.getTurmaId());
        if (aberto.isPresent() && mesmoOficineiro(aberto.get().getOficineiro(), atual)) {
            return;
        }
        LocalDate hoje = LocalDate.now();
        aberto.ifPresent(periodo -> {
            periodo.setDataFim(fimDoPeriodo(periodo.getDataInicio(), hoje));
            // ver comentário em registrar(Oficina): fecha antes de abrir o novo período.
            turmaHistoricoRepository.saveAndFlush(periodo);
        });
        if (atual != null) {
            turmaHistoricoRepository.save(TurmaOficineiroHistorico.builder()
                    .turma(turma).oficineiro(atual).dataInicio(hoje).build());
            // Só houve troca de verdade se já existia um período aberto antes.
            if (aberto.isPresent()) {
                aulaRepository.reatribuirOficineiroAulasFuturas(turma, atual, hoje);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OficineiroHistoricoDTO> listarPorOficina(Long oficinaId) {
        return oficinaHistoricoRepository.findByOficina_OficinaIdOrderByDataInicioDescHistoricoIdDesc(oficinaId).stream()
                .map(h -> new OficineiroHistoricoDTO(h.getOficineiro().getUsuarioId(), h.getOficineiro().getNomeCompleto(),
                        h.getDataInicio(), h.getDataFim()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OficineiroHistoricoDTO> listarPorTurma(Long turmaId) {
        return turmaHistoricoRepository.findByTurma_TurmaIdOrderByDataInicioDescHistoricoIdDesc(turmaId).stream()
                .map(h -> new OficineiroHistoricoDTO(h.getOficineiro().getUsuarioId(), h.getOficineiro().getNomeCompleto(),
                        h.getDataInicio(), h.getDataFim()))
                .toList();
    }

    private boolean mesmoOficineiro(Usuario a, Usuario b) {
        return a != null && b != null && Objects.equals(a.getUsuarioId(), b.getUsuarioId());
    }

    /** O período anterior termina ontem — ou no mesmo dia, se trocou no dia em que começou. */
    private LocalDate fimDoPeriodo(LocalDate inicio, LocalDate hoje) {
        LocalDate ontem = hoje.minusDays(1);
        return ontem.isBefore(inicio) ? inicio : ontem;
    }
}
