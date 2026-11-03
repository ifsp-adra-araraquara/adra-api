package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.mapper.PresencaMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.RegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.FaltaJustificadaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.RegistroDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * FT-13: a suspensão justifica a falta sozinha. Quem está suspenso no dia vem e fica
 * como falta justificada por Suspensão na chamada, e registrar, alterar ou arquivar uma
 * suspensão corrige as chamadas já lançadas no período (CA-FT13.2 e CA-FT13.3).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FaltaPorSuspensaoService {

    private final RegistroDisciplinarRepository registroRepository;
    private final ChamadaRepository chamadaRepository;
    private final PresencaRepository presencaRepository;
    private final FaltaJustificadaRepository faltaJustificadaRepository;
    private final TurmaAlunosRepository turmaAlunosRepository;
    private final PresencaMapper presencaMapper;

    /** Fim da suspensão que cobre a data, por atendido. Só vêm os suspensos. */
    @Transactional(readOnly = true)
    public Map<Long, LocalDate> suspensosEm(Collection<Long> assistidoIds, LocalDate data) {
        if (assistidoIds.isEmpty()) {
            return Map.of();
        }
        return registroRepository.findCandidatosASinalizacao(assistidoIds).stream()
                .filter(registro -> registro.suspensoEm(data))
                .collect(Collectors.toMap(registro -> registro.getAssistido().getAssistidoId(),
                        RegistroDisciplinar::getSuspensaoFim,
                        (um, outro) -> um.isAfter(outro) ? um : outro));
    }

    /**
     * Acerta as chamadas já lançadas do atendido entre as datas: suspenso no dia vira falta
     * justificada por Suspensão; quem deixou de estar suspenso (suspensão encurtada ou
     * arquivada) volta a Falta. Dias futuros ainda não têm chamada e se resolvem ao abri-la.
     */
    public void sincronizar(Assistido assistido, LocalDate de, LocalDate ate, Usuario usuario) {
        LocalDate hoje = LocalDate.now();
        LocalDate limite = ate.isAfter(hoje) ? hoje : ate;
        if (limite.isBefore(de)) {
            return;
        }
        Long assistidoId = assistido.getAssistidoId();
        for (Chamada chamada : chamadaRepository.findByDataChamadaBetween(de, limite)) {
            LocalDate dia = chamada.getDataChamada();
            boolean estavaNoGrupo = turmaAlunosRepository.findElegiveisParaChamada(chamada.getTurma(), dia).stream()
                    .anyMatch(vinculo -> Objects.equals(vinculo.getAssistido().getAssistidoId(), assistidoId));
            if (!estavaNoGrupo) {
                continue;
            }
            boolean suspenso = suspensosEm(List.of(assistidoId), dia).containsKey(assistidoId);
            Presenca presenca = presencaRepository.findByChamadaAndAssistido(chamada, assistido).orElse(null);
            if (suspenso) {
                marcarFaltaPorSuspensao(chamada, assistido, presenca, usuario);
            } else if (presenca != null && foiPorSuspensao(presenca)) {
                presenca.setStatusPresenca(StatusPresenca.FALTA);
                presenca.setAtualizadoPor(usuario);
                presencaMapper.sincronizarFaltaJustificada(presencaRepository.save(presenca), null, null);
            }
        }
    }

    private void marcarFaltaPorSuspensao(Chamada chamada, Assistido assistido, Presenca presenca, Usuario usuario) {
        if (presenca == null) {
            presenca = new Presenca();
            presenca.setChamada(chamada);
            presenca.setAssistido(assistido);
            presenca.setCriadoPor(usuario);
        }
        presenca.setStatusPresenca(StatusPresenca.FALTA_JUSTIFICADA);
        presenca.setAtualizadoPor(usuario);
        presenca = presencaRepository.save(presenca);
        presencaMapper.sincronizarFaltaJustificada(presenca, MotivoFalta.SUSPENSAO, null);
    }

    private boolean foiPorSuspensao(Presenca presenca) {
        return presenca.getStatusPresenca() == StatusPresenca.FALTA_JUSTIFICADA
                && faltaJustificadaRepository.findByPresenca_PresencaId(presenca.getPresencaId())
                        .map(fj -> fj.getMotivoFalta() == MotivoFalta.SUSPENSAO)
                        .orElse(false);
    }
}
