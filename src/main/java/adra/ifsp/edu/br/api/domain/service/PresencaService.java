package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.PresencaMapper;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PresencaService {

    private final PresencaRepository presencaRepository;
    private final AulaRepository aulaRepository;
    private final AssistidoRepository assistidoRepository;
    private final PresencaMapper presencaMapper;
    private final AuditoriaService auditoriaService;

    public PresencaResponseDTO registrarPresenca(PresencaRequestDTO dto) {
        Presenca presenca = presencaMapper.paraNovaEntidade(dto);
        presenca = presencaRepository.save(presenca);

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "aulaId", presenca.getAula().getAulaId().toString(),
                        "assistidoId", presenca.getAssistido().getAssistidoId().toString(),
                        "statusPresenca", presenca.getStatusPresenca().name()
                ),
                "Registro de presença"
        );

        return presencaMapper.paraDTO(presenca);
    }

    public List<PresencaResponseDTO> registrarChamada(Long idAula, List<PresencaRequestDTO> presencas) {
        Aula aula = aulaRepository.findById(idAula)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + idAula));

        List<Presenca> presencaEntities = presencas.stream()
                .map(dto -> {
                    Presenca presenca = presencaMapper.paraNovaEntidade(dto);
                    presenca.setAula(aula);
                    return presenca;
                })
                .collect(Collectors.toList());

        List<Presenca> salvas = presencaRepository.saveAll(presencaEntities);

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "chamada",
                idAula,
                AcaoSistema.CRIAR,
                null,
                Map.of("quantidadePresencas", String.valueOf(salvas.size())),
                "Registro de chamada em lote"
        );

        return salvas.stream()
                .map(presencaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public List<PresencaResponseDTO> buscarPorAula(Long idAula) {
        Aula aula = aulaRepository.findById(idAula)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + idAula));

        List<Presenca> presencas = presencaRepository.findByAula(aula);

        return presencas.stream()
                .map(presencaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public List<PresencaResponseDTO> buscarPorAssistido(Long idAssistido) {
        Assistido assistido = assistidoRepository.findById(idAssistido)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + idAssistido));

        List<Presenca> presencas = presencaRepository.findByAssistido(assistido);

        return presencas.stream()
                .map(presencaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public PresencaResponseDTO atualizarPresenca(Long id, PresencaRequestDTO dto) {
        Presenca presenca = buscarEntidadePorId(id);

        Map<String, Object> valorAnterior = Map.of(
                "statusPresenca", presenca.getStatusPresenca().name(),
                "justificativaFalta", presenca.getJustificativaFalta()
        );

        presencaMapper.atualizarEntidade(presenca, dto);
        presenca = presencaRepository.save(presenca);

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of(
                        "statusPresenca", presenca.getStatusPresenca().name(),
                        "justificativaFalta", presenca.getJustificativaFalta()
                ),
                "Atualização de presença"
        );

        return presencaMapper.paraDTO(presenca);
    }

    public void deletarPresenca(Long id) {
        Presenca presenca = buscarEntidadePorId(id);
        presencaRepository.delete(presenca);

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                AcaoSistema.EXCLUIR,
                Map.of(
                        "aulaId", presenca.getAula().getAulaId().toString(),
                        "assistidoId", presenca.getAssistido().getAssistidoId().toString()
                ),
                null,
                "Exclusão de presença"
        );
    }

    private Presenca buscarEntidadePorId(Long id) {
        return presencaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Presença não encontrada: id " + id));
    }
}
