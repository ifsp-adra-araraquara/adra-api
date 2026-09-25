package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.FaltaJustificada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.FaltaJustificadaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PresencaMapper {

    private final AulaRepository aulaRepository;
    private final AssistidoRepository assistidoRepository;
    private final FaltaJustificadaRepository faltaJustificadaRepository;

    public Presenca paraNovaEntidade(PresencaRequestDTO dto) {
        Presenca presenca = new Presenca();
        atualizarEntidade(presenca, dto);
        return presenca;
    }

    public void atualizarEntidade(Presenca presenca, PresencaRequestDTO dto) {
        if (dto.aulaId() != null) {
            Aula aula = aulaRepository.findById(dto.aulaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + dto.aulaId()));
            presenca.setAula(aula);
        }
        if (dto.assistidoId() != null) {
            Assistido assistido = assistidoRepository.findById(dto.assistidoId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + dto.assistidoId()));
            presenca.setAssistido(assistido);
        }
        presenca.setStatusPresenca(dto.statusPresenca());
    }

    /**
     * Sincroniza o satélite FaltaJustificada com o status já salvo em `presenca`.
     * Só chame depois do save (precisa de presenca.getPresencaId() != null).
     * CA-65.2: FALTA_JUSTIFICADA exige motivo_falta; motivo OUTRO exige observacao.
     * Fora de FALTA_JUSTIFICADA, remove qualquer FaltaJustificada residual de um
     * reenvio anterior (CA-65.4 — reenvio atualiza, não deixa lixo pra trás).
     */
    public void sincronizarFaltaJustificada(Presenca presenca, PresencaRequestDTO dto) {
        if (presenca.getStatusPresenca() != StatusPresenca.FALTA_JUSTIFICADA) {
            faltaJustificadaRepository.deleteByPresenca_PresencaId(presenca.getPresencaId());
            return;
        }

        if (dto.motivoFalta() == null) {
            throw new RegraNegocioException("Falta justificada exige o motivo da falta.");
        }
        if (dto.motivoFalta() == MotivoFalta.OUTRO
                && (dto.observacao() == null || dto.observacao().isBlank())) {
            throw new RegraNegocioException("Motivo OUTRO exige observação preenchida.");
        }

        FaltaJustificada faltaJustificada = faltaJustificadaRepository
                .findByPresenca_PresencaId(presenca.getPresencaId())
                .orElseGet(() -> {
                    FaltaJustificada nova = new FaltaJustificada();
                    nova.setPresenca(presenca);
                    return nova;
                });

        faltaJustificada.setMotivoFalta(dto.motivoFalta());
        faltaJustificada.setObservacao(dto.observacao());
        faltaJustificadaRepository.save(faltaJustificada);
    }

    /** Para 1 presença isolada (busca a falta justificada, se houver). */
    public PresencaResponseDTO paraDTO(Presenca presenca) {
        FaltaJustificada faltaJustificada = faltaJustificadaRepository
                .findByPresenca_PresencaId(presenca.getPresencaId())
                .orElse(null);
        return PresencaResponseDTO.fromEntity(presenca, faltaJustificada);
    }

    /** Para o GET em lote — 1 query pra buscar todas as faltas justificadas, em vez de N+1. */
    public List<PresencaResponseDTO> paraDTOList(List<Presenca> presencas) {
        List<Long> ids = presencas.stream().map(Presenca::getPresencaId).toList();

        Map<Long, FaltaJustificada> faltasPorPresencaId = faltaJustificadaRepository
                .findByPresenca_PresencaIdIn(ids)
                .stream()
                .collect(Collectors.toMap(fj -> fj.getPresenca().getPresencaId(), fj -> fj));

        return presencas.stream()
                .map(p -> PresencaResponseDTO.fromEntity(p, faltasPorPresencaId.get(p.getPresencaId())))
                .toList();
    }

    /**
     * Modelo esparso: monta a chamada completa de uma aula cruzando o roster
     * (quem estava vinculado à turma na data da aula, via TurmaAlunos) com os
     * registros de falta daquela aula. Quem está no roster e não tem falta
     * registrada volta como PRESENTE (inferido, sem linha no banco).
     */
    public List<PresencaResponseDTO> montarChamadaCompleta(
            Long idAula, List<TurmaAlunos> vinculosAtivos, List<Presenca> faltas) {

        Map<Long, Presenca> faltaPorAssistidoId = faltas.stream()
                .collect(Collectors.toMap(p -> p.getAssistido().getAssistidoId(), p -> p));

        List<Long> presencaIds = faltas.stream().map(Presenca::getPresencaId).toList();

        Map<Long, FaltaJustificada> faltaJustificadaPorPresencaId = faltaJustificadaRepository
                .findByPresenca_PresencaIdIn(presencaIds)
                .stream()
                .collect(Collectors.toMap(fj -> fj.getPresenca().getPresencaId(), fj -> fj));

        return vinculosAtivos.stream()
                .map(TurmaAlunos::getAssistido)
                .map(assistido -> {
                    Presenca falta = faltaPorAssistidoId.get(assistido.getAssistidoId());
                    if (falta == null) {
                        return PresencaResponseDTO.presente(idAula, assistido.getAssistidoId(), assistido.getNomeCompleto());
                    }
                    return PresencaResponseDTO.fromEntity(falta, faltaJustificadaPorPresencaId.get(falta.getPresencaId()));
                })
                .toList();
    }
}
