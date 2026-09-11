package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.mapper.AssistidoMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.DuplicidadeProvavelException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssistidoService {

    private final AssistidoRepository assistidoRepository;
    private final AssistidoMapper assistidoMapper;
    private final TurmaRepository turmaRepository;
    private final AuditoriaService auditoriaService;

    public AssistidoResponseDTO cadastrar(AssistidoRequestDTO dto) {
        if (!dto.confirmarApesarDeDuplicidade()) {
            verificarDuplicidade(dto.nomeCompleto(), dto.dataNascimento(), null);
        }

        Assistido assistido = assistidoMapper.paraNovaEntidade(dto);

        if (dto.turmaId() != null) {
            Turma turma = turmaRepository.findById(dto.turmaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma nao encontrada: id " + dto.turmaId()));
            assistido.setTurma(turma);
        }

        assistido = assistidoRepository.save(assistido);

        auditoriaService.registrar(
                ModuloSistema.ASSISTIDOS,
                "assistido",
                assistido.getAssistidoId(),
                AcaoSistema.CRIAR,
                null,
                Map.of("nomeCompleto", assistido.getNomeCompleto(), "status", assistido.getStatus().name()),
                "Cadastro de assistido"
        );

        return assistidoMapper.paraDTO(assistido);
    }

    @Transactional(readOnly = true)
    public AssistidoResponseDTO buscarPorId(Long id) {
        return assistidoMapper.paraDTO(buscarEntidadePorId(id));
    }

    @Transactional(readOnly = true)
    public List<AssistidoResponseDTO> listar() {
        return assistidoRepository.findAll().stream()
                .map(assistidoMapper::paraDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaginaDTO<AssistidoResponseDTO> listarPaginado(String busca, Long turmaId, StatusGeral status, int pagina, int tamanho) {
        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "nomeCompleto"));
        Specification<Assistido> spec = AssistidoSpecification.comFiltros(busca, turmaId, status);
        Page<AssistidoResponseDTO> page = assistidoRepository.findAll(spec, pageable)
                .map(assistidoMapper::paraDTO);
        return PaginaDTO.de(page);
    }

    public AssistidoResponseDTO atualizar(Long id, AssistidoRequestDTO dto) {
        Assistido assistido = buscarEntidadePorId(id);

        if (!dto.confirmarApesarDeDuplicidade()) {
            verificarDuplicidade(dto.nomeCompleto(), dto.dataNascimento(), id);
        }

        Map<String, Object> valorAnterior = Map.of(
                "nomeCompleto", assistido.getNomeCompleto(),
                "dataNascimento", assistido.getDataNascimento().toString()
        );

        assistidoMapper.atualizarEntidade(assistido, dto);

        if (dto.turmaId() != null) {
            Turma turma = turmaRepository.findById(dto.turmaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma nao encontrada: id " + dto.turmaId()));
            assistido.setTurma(turma);
        } else {
            assistido.setTurma(null);
        }

        assistido = assistidoRepository.save(assistido);

        auditoriaService.registrar(
                ModuloSistema.ASSISTIDOS,
                "assistido",
                assistido.getAssistidoId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of("nomeCompleto", assistido.getNomeCompleto(), "dataNascimento", assistido.getDataNascimento().toString()),
                "Atualizacao de dados cadastrais"
        );

        return assistidoMapper.paraDTO(assistido);
    }

    /** Muda o status (ex.: encerrar vinculo com a instituicao) - fluxo separado da edicao cadastral. */
    public AssistidoResponseDTO alterarStatus(Long id, AssistidoStatusRequestDTO dto) {
        Assistido assistido = buscarEntidadePorId(id);

        var statusAnterior = assistido.getStatus();
        assistido.setStatus(dto.status());
        assistido.setDataSaida(dto.dataSaida());
        assistido.setMotivoSaida(dto.motivoSaida());
        assistido = assistidoRepository.save(assistido);

        auditoriaService.registrar(
                ModuloSistema.ASSISTIDOS,
                "assistido",
                assistido.getAssistidoId(),
                AcaoSistema.EDITAR,
                Map.of("status", statusAnterior.name()),
                Map.of("status", assistido.getStatus().name()),
                "Alteracao de status do assistido"
        );

        return assistidoMapper.paraDTO(assistido);
    }

    Assistido buscarEntidadePorId(Long id) {
        return assistidoRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido nao encontrado: id " + id));
    }

    private void verificarDuplicidade(String nomeCompleto, LocalDate dataNascimento, Long idEmEdicao) {
        List<AssistidoResponseDTO> duplicados = assistidoRepository
                .findByNomeCompletoIgnoreCaseAndDataNascimento(nomeCompleto, dataNascimento)
                .stream()
                .filter(a -> idEmEdicao == null || !a.getAssistidoId().equals(idEmEdicao))
                .map(assistidoMapper::paraDTO)
                .collect(Collectors.toList());

        if (!duplicados.isEmpty()) {
            throw new DuplicidadeProvavelException(duplicados);
        }
    }
}
