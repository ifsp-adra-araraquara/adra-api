package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.mapper.AssistidoMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavelId;
import adra.ifsp.edu.br.api.domain.model.AssistidoTurmaHistorico;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.AssistidoTurmaHistoricoRepository;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.DuplicidadeProvavelException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssistidoService {

    private final AssistidoRepository assistidoRepository;
    private final AssistidoMapper assistidoMapper;
    private final TurmaRepository turmaRepository;
    private final ResponsavelRepository responsavelRepository;
    private final AssistidoTurmaHistoricoRepository turmaHistoricoRepository;
    private final AuditoriaService auditoriaService;

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

        // Abre o primeiro periodo de historico de turma (CA-A04.2), se ja
        // entrou vinculado a uma turma.
        if (turma != null) {
            turmaHistoricoRepository.save(AssistidoTurmaHistorico.builder()
                    .assistido(assistido)
                    .turma(turma)
                    .build());
        }

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

    /**
     * CA-A04: atualiza dados cadastrais, troca de turma (preservando
     * historico) e responsaveis vinculados numa unica operacao atomica -
     * a classe e' @Transactional, entao qualquer excecao aqui reverte as
     * tres partes juntas (CA-A04.4).
     */
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
        atualizarTurma(assistido, dto.turmaId());
        atualizarResponsaveis(assistido, dto.responsaveisVinculados(), dto.responsaveis());

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

    /**
     * CA-A04.2: so mexe no historico se a turma realmente mudou. Fecha o
     * periodo aberto (se houver) e abre um novo - nunca sobrescreve.
     */
    private void atualizarTurma(Assistido assistido, Long novaTurmaId) {
        Long turmaAtualId = assistido.getTurma() != null ? assistido.getTurma().getTurmaId() : null;
        if (Objects.equals(turmaAtualId, novaTurmaId)) {
            return;
        }

        turmaHistoricoRepository.findByAssistido_AssistidoIdAndDataFimIsNull(assistido.getAssistidoId())
                .ifPresent(historico -> historico.setDataFim(OffsetDateTime.now()));

        Turma novaTurma = null;
        if (novaTurmaId != null) {
            novaTurma = turmaRepository.findById(novaTurmaId)
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma nao encontrada: id " + novaTurmaId));
            if (!Boolean.TRUE.equals(novaTurma.getAtivo())) {
                throw new RegraNegocioException("A turma informada nao esta ativa");
            }
            turmaHistoricoRepository.save(AssistidoTurmaHistorico.builder()
                    .assistido(assistido)
                    .turma(novaTurma)
                    .build());
        }

        assistido.setTurma(novaTurma);
    }

    /**
     * CA-A04.3: reconcilia os vinculos de responsavel do assistido a partir
     * de duas listas - as que devem PERMANECER (responsaveisVinculados,
     * identificadas por responsavelId) e as NOVAS a cadastrar e vincular
     * (responsaveisNovos). Qualquer vinculo atual que nao aparecer entre as
     * que devem permanecer e' desvinculado.
     * <p>
     * Se nenhuma das duas listas vier no payload (ambas null), esta chamada
     * nao esta gerenciando responsaveis - mantem como esta.
     */
    private void atualizarResponsaveis(Assistido assistido,
                                        List<VinculoFamiliarRequestDTO> responsaveisVinculados,
                                        List<VinculoFamiliarComResponsavelRequestDTO> responsaveisNovos) {
        if (responsaveisVinculados == null && responsaveisNovos == null) {
            return;
        }

        List<VinculoFamiliarRequestDTO> paraManter = responsaveisVinculados == null ? List.of() : responsaveisVinculados;
        List<VinculoFamiliarComResponsavelRequestDTO> novos = responsaveisNovos == null ? List.of() : responsaveisNovos;

        Set<Long> idsParaManter = paraManter.stream()
                .map(VinculoFamiliarRequestDTO::responsavelId)
                .collect(Collectors.toSet());

        List<AssistidoResponsavel> paraRemover = assistido.getVinculosFamiliares().stream()
                .filter(vinculo -> !idsParaManter.contains(vinculo.getResponsavel().getResponsavelId()))
                .toList();

        if (idsParaManter.size() + novos.size() == 0) {
            throw new RegraNegocioException("O assistido deve manter pelo menos um responsavel vinculado");
        }

        if (!paraRemover.isEmpty() && !usuarioAtualEhAdministrador()) {
            throw new AcessoNegadoException("Somente o administrador pode remover um responsavel vinculado");
        }

        paraRemover.forEach(vinculo -> assistido.getVinculosFamiliares().remove(vinculo));

        for (VinculoFamiliarRequestDTO dtoVinculo : paraManter) {
            AssistidoResponsavel vinculo = assistido.getVinculosFamiliares().stream()
                    .filter(v -> v.getResponsavel().getResponsavelId().equals(dtoVinculo.responsavelId()))
                    .findFirst()
                    .orElseThrow(() -> new EntidadeNaoEncontradaException(
                            "Vinculo nao encontrado para o responsavel id " + dtoVinculo.responsavelId()));

            vinculo.setParentesco(dtoVinculo.parentesco());
            vinculo.setResponsavelPrincipal(dtoVinculo.responsavelPrincipal());
            vinculo.setContatoEmergencia(dtoVinculo.contatoEmergencia());
            vinculo.setAutorizadoRetirada(dtoVinculo.autorizadoRetirada());
            vinculo.setObservacoes(dtoVinculo.observacoes());
        }

        for (VinculoFamiliarComResponsavelRequestDTO dtoNovo : novos) {
            Responsavel responsavel = (dtoNovo.cpf() != null && !dtoNovo.cpf().isBlank())
                    ? responsavelRepository.findByCpf(dtoNovo.cpf()).orElseGet(() -> criarNovoResponsavel(dtoNovo))
                    : criarNovoResponsavel(dtoNovo);

            AssistidoResponsavel vinculo = AssistidoResponsavel.builder()
                    .id(new AssistidoResponsavelId(assistido.getAssistidoId(), responsavel.getResponsavelId()))
                    .assistido(assistido)
                    .responsavel(responsavel)
                    .parentesco(dtoNovo.parentesco())
                    .responsavelPrincipal(dtoNovo.responsavelPrincipal())
                    .contatoEmergencia(dtoNovo.contatoEmergencia())
                    .autorizadoRetirada(dtoNovo.autorizadoRetirada())
                    .observacoes(dtoNovo.observacoes())
                    .build();

            assistido.getVinculosFamiliares().add(vinculo);
        }

        long totalPrincipais = assistido.getVinculosFamiliares().stream()
                .filter(AssistidoResponsavel::isResponsavelPrincipal)
                .count();

        if (totalPrincipais == 0) {
            throw new RegraNegocioException("Pelo menos um responsavel deve ser marcado como responsavel principal");
        }
        if (totalPrincipais > 1) {
            throw new RegraNegocioException("Apenas um responsavel pode ser marcado como responsavel principal");
        }
    }

    private boolean usuarioAtualEhAdministrador() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(autoridade -> autoridade.getAuthority().equals("ROLE_ADMINISTRADOR"));
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
