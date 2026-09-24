package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.VinculoTurmaAssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.mapper.AssistidoMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavelId;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.DuplicidadeProvavelException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private final ResponsavelRepository responsavelRepository;
    private final AuditoriaService auditoriaService;
    private final VinculoTurmaService vinculoTurmaService;
    private final TurmaAlunosRepository turmaAlunosRepository;

    public AssistidoResponseDTO cadastrar(AssistidoRequestDTO dto) {
        if (!dto.confirmarApesarDeDuplicidade()) {
            verificarDuplicidade(dto.nomeCompleto(), dto.dataNascimento(), null);
        }

        // CA-A02.3: Validar pelo menos um responsável
        if (dto.responsaveis() == null || dto.responsaveis().isEmpty()) {
            throw new RegraNegocioException("Pelo menos um responsavel deve ser informado");
        }

        // CA-A02.3: Validar turma ativa
        Turma turma = null;
        if (dto.turmaId() != null) {
            turma = turmaRepository.findById(dto.turmaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma nao encontrada: id " + dto.turmaId()));
            if (!turma.getAtivo()) {
                throw new RegraNegocioException("A turma informada nao esta ativa");
            }
        }

        Assistido assistido = assistidoMapper.paraNovaEntidade(dto);
        assistido.setTurma(turma);

        // CA-A02.4: Criar assistido e vínculos em transação atômica
        assistido = assistidoRepository.save(assistido);
        criarVinculosResponsaveis(assistido, dto.responsaveis());

        // Mantém turma_aluno em dia — é o que o CA-65 (chamada) usa pra montar
        // o roster de uma aula, não Assistido.turma diretamente.
        vinculoTurmaService.vincular(assistido, turma);

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

    @Transactional(readOnly = true)
    public Map<String, Object> verificarDuplicidade(String nomeCompleto, String dataNascimento) {
        LocalDate dataNasc = LocalDate.parse(dataNascimento);
        List<AssistidoResponseDTO> duplicados = assistidoRepository
                .findByNomeCompletoIgnoreCaseAndDataNascimento(nomeCompleto, dataNasc)
                .stream()
                .map(assistidoMapper::paraDTO)
                .collect(Collectors.toList());

        return Map.of(
                "possivelDuplicidade", !duplicados.isEmpty(),
                "duplicados", duplicados
        );
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

        Turma turma = null;
        if (dto.turmaId() != null) {
            turma = turmaRepository.findById(dto.turmaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma nao encontrada: id " + dto.turmaId()));
            assistido.setTurma(turma);
        } else {
            assistido.setTurma(null);
        }

        assistido = assistidoRepository.save(assistido);
        vinculoTurmaService.vincular(assistido, turma);

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

    /**
     * Histórico de turmas do assistido (turma_aluno) — aba "Turmas" do modal
     * do assistido, só pro coordenador (ver AssistidoController).
     */
    @Transactional(readOnly = true)
    public List<VinculoTurmaAssistidoResponseDTO> listarHistoricoTurmas(Long id) {
        Assistido assistido = buscarEntidadePorId(id);
        return turmaAlunosRepository.findByAssistidoOrderByDataEntradaDesc(assistido).stream()
                .map(VinculoTurmaAssistidoResponseDTO::fromEntity)
                .collect(Collectors.toList());
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

    /**
     * Cria responsáveis e vínculos em transação atômica.
     * Se o responsável já existir pelo CPF, reutiliza-o.
     * Garante que há pelo menos um responsável principal.
     */
    private void criarVinculosResponsaveis(Assistido assistido, List<VinculoFamiliarComResponsavelRequestDTO> responsaveisDTO) {
        boolean temResponsavelPrincipal = false;

        for (VinculoFamiliarComResponsavelRequestDTO vinculoDTO : responsaveisDTO) {
            Responsavel responsavel;

            // Buscar responsável existente pelo CPF ou criar novo
            if (vinculoDTO.cpf() != null && !vinculoDTO.cpf().isBlank()) {
                responsavel = responsavelRepository.findByCpf(vinculoDTO.cpf())
                        .orElseGet(() -> criarNovoResponsavel(vinculoDTO));
            } else {
                responsavel = criarNovoResponsavel(vinculoDTO);
            }

            // Criar vínculo
            AssistidoResponsavel vinculo = AssistidoResponsavel.builder()
                    .id(new AssistidoResponsavelId(assistido.getAssistidoId(), responsavel.getResponsavelId()))
                    .assistido(assistido)
                    .responsavel(responsavel)
                    .parentesco(vinculoDTO.parentesco())
                    .responsavelPrincipal(vinculoDTO.responsavelPrincipal())
                    .contatoEmergencia(vinculoDTO.contatoEmergencia())
                    .autorizadoRetirada(vinculoDTO.autorizadoRetirada())
                    .observacoes(vinculoDTO.observacoes())
                    .build();

            assistido.getVinculosFamiliares().add(vinculo);

            if (vinculoDTO.responsavelPrincipal()) {
                temResponsavelPrincipal = true;
            }
        }

        // Validar que há pelo menos um responsável principal
        if (!temResponsavelPrincipal) {
            throw new RegraNegocioException("Pelo menos um responsavel deve ser marcado como responsavel principal");
        }
    }

    private Responsavel criarNovoResponsavel(VinculoFamiliarComResponsavelRequestDTO dto) {
        ResponsavelRequestDTO responsavelDTO = new ResponsavelRequestDTO(
                dto.nomeCompleto(),
                dto.dataNascimento(),
                dto.cpf(),
                dto.telefone(),
                dto.email(),
                dto.endereco(),
                dto.observacoes()
        );

        Responsavel responsavel = Responsavel.builder()
                .nomeCompleto(responsavelDTO.nomeCompleto())
                .dataNascimento(responsavelDTO.dataNascimento())
                .cpf(responsavelDTO.cpf())
                .telefone(responsavelDTO.telefone())
                .email(responsavelDTO.email())
                .endereco(responsavelDTO.endereco())
                .observacoes(responsavelDTO.observacoes())
                .build();

        return responsavelRepository.save(responsavel);
    }
}
