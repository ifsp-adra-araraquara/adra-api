package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoContagemDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.VinculoTurmaAssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.*;
import adra.ifsp.edu.br.api.domain.mapper.AssistidoMapper;
import adra.ifsp.edu.br.api.domain.mapper.ResponsavelMapper;
import adra.ifsp.edu.br.api.exception.ArquivoInvalidoException;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavelId;
import adra.ifsp.edu.br.api.domain.model.AssistidoTurmaHistorico;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.AssistidoTurmaHistoricoRepository;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssistidoService {
    private final ArquivoService arquivoService;
    private final AssistidoRepository assistidoRepository;
    private final AssistidoMapper assistidoMapper;
    private final ResponsavelMapper responsavelMapper;
    private final ElegibilidadeService elegibilidadeService;
    private final AssistidoIndicadoresService indicadoresService;
    private final TurmaRepository turmaRepository;
    private final ResponsavelRepository responsavelRepository;
    private final AssistidoTurmaHistoricoRepository turmaHistoricoRepository;
    private final AuditoriaService auditoriaService;
    private final VinculoTurmaService vinculoTurmaService;
    private final TurmaAlunosRepository turmaAlunosRepository;

    AssistidoResponseDTO cadastrar(AssistidoRequestDTO dto) {
        validarDadosComplementares(dto);
        // Regra de corte so na entrada: familia ja atendida nao e' barrada ao editar.
        elegibilidadeService.validarRendaFamiliar(dto.rendaFamiliar());
        verificarDuplicidade(dto, null);

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

    /** Primeiro passo do cadastro: se o CPF já existe, o front abre a ficha em vez de cadastrar de novo. */
    @Transactional(readOnly = true)
    public AssistidoResponseDTO buscarPorCpf(String cpf) {
        return assistidoRepository.findByCpf(cpf)
                .map(assistidoMapper::paraDTO)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido nao encontrado para o CPF informado"));
    }

    @Transactional(readOnly = true)
    public List<AssistidoResponseDTO> listar() {
        return assistidoRepository.findAll().stream()
                .map(assistidoMapper::paraDTO)
                .collect(Collectors.toList());
    }

    /**
     * Listagem paginada com responsável principal e indicadores de frequência
     * por linha. {@code emAcompanhamento=true} lista só ativos em acompanhamento
     * (ver {@link IndicadorFrequencia}) — como é um valor calculado, esse filtro
     * calcula os indicadores de todos os ativos do recorte e pagina em memória.
     */
    @Transactional(readOnly = true)
    public PaginaDTO<AssistidoResponseDTO> listarPaginado(String busca, Long turmaId, Long oficinaId, StatusGeral status,
                                                          boolean emAcompanhamento, int pagina, int tamanho) {
        Sort porNome = Sort.by(Sort.Direction.ASC, "nomeCompleto");

        if (emAcompanhamento) {
            Specification<Assistido> spec = AssistidoSpecification.comFiltros(busca, turmaId, oficinaId, StatusGeral.ATIVO);
            List<Assistido> ativos = assistidoRepository.findAll(spec, porNome);
            Map<Long, IndicadorFrequencia> frequencias = indicadoresService.frequencias(ativos);
            List<Assistido> filtrados = ativos.stream()
                    .filter(a -> frequencias.get(a.getAssistidoId()).emAcompanhamento())
                    .toList();

            int inicio = Math.min(pagina * tamanho, filtrados.size());
            int fim = Math.min(inicio + tamanho, filtrados.size());
            List<Assistido> fatia = filtrados.subList(inicio, fim);
            return new PaginaDTO<>(
                    paraLinhas(fatia, frequencias, turmaId),
                    pagina,
                    tamanho,
                    filtrados.size(),
                    (int) Math.ceil(filtrados.size() / (double) tamanho)
            );
        }

        Specification<Assistido> spec = AssistidoSpecification.comFiltros(busca, turmaId, oficinaId, status);
        Page<Assistido> page = assistidoRepository.findAll(spec, PageRequest.of(pagina, tamanho, porNome));
        List<AssistidoResponseDTO> linhas = paraLinhas(page.getContent(), indicadoresService.frequencias(page.getContent()), turmaId);
        return new PaginaDTO<>(linhas, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Totais das abas (Todos / Ativos / Inativos / Em acompanhamento) para o recorte de busca, turma e oficina. */
    @Transactional(readOnly = true)
    public AssistidoContagemDTO contar(String busca, Long turmaId, Long oficinaId) {
        long todos = assistidoRepository.count(AssistidoSpecification.comFiltros(busca, turmaId, oficinaId, null));
        List<Assistido> ativos = assistidoRepository.findAll(
                AssistidoSpecification.comFiltros(busca, turmaId, oficinaId, StatusGeral.ATIVO));
        long emAcompanhamento = indicadoresService.frequencias(ativos).values().stream()
                .filter(IndicadorFrequencia::emAcompanhamento)
                .count();
        long inativos = assistidoRepository.count(AssistidoSpecification.comFiltros(busca, turmaId, oficinaId, StatusGeral.INATIVO));
        return new AssistidoContagemDTO(todos, ativos.size(), inativos, emAcompanhamento);
    }

    private List<AssistidoResponseDTO> paraLinhas(List<Assistido> assistidos, Map<Long, IndicadorFrequencia> frequencias,
                                                  Long turmaId) {
        Map<Long, AssistidoResponsavel> responsaveis = indicadoresService.responsaveisPrincipais(assistidos);

        // Filtro por turma: vínculo mais recente de cada assistido nessa turma (entrada/saída).
        Map<Long, TurmaAlunos> periodoNaTurma = new java.util.HashMap<>();
        if (turmaId != null && !assistidos.isEmpty()) {
            for (TurmaAlunos v : turmaAlunosRepository.findByTurma_TurmaIdAndAssistidoIn(turmaId, assistidos)) {
                periodoNaTurma.merge(v.getAssistido().getAssistidoId(), v,
                        (a, b) -> a.getDataEntrada().isAfter(b.getDataEntrada()) ? a : b);
            }
        }

        boolean somenteResumo = usuarioAtualTemPapel("ROLE_OFICINEIRO");
        return assistidos.stream()
                .map(a -> {
                    AssistidoResponseDTO dto = assistidoMapper.paraDTO(
                            a, responsaveis.get(a.getAssistidoId()), frequencias.get(a.getAssistidoId()));
                    if (somenteResumo) return dto.resumo();
                    TurmaAlunos periodo = periodoNaTurma.get(a.getAssistidoId());
                    return periodo == null ? dto : dto.comPeriodoNaTurma(periodo.getDataEntrada(), periodo.getDataSaida());
                })
                .toList();
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

        validarDadosComplementares(dto);
        verificarDuplicidade(dto, id);

        Map<String, Object> valorAnterior = Map.of(
                "nomeCompleto", assistido.getNomeCompleto(),
                "dataNascimento", assistido.getDataNascimento().toString()
        );

        assistidoMapper.atualizarEntidade(assistido, dto);
        atualizarTurma(assistido, dto.turmaId());
        atualizarResponsaveis(assistido, dto.responsaveisVinculados(), dto.responsaveis());

        assistido = assistidoRepository.save(assistido);
        vinculoTurmaService.vincular(assistido, assistido.getTurma());

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

        if (!paraRemover.isEmpty() && !usuarioAtualTemPapel("ROLE_ADMINISTRADOR")) {
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

    private boolean usuarioAtualTemPapel(String papel) {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(autoridade -> autoridade.getAuthority().equals(papel));
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

    /** CPF repetido bloqueia; mesmo nome + nascimento so passa se os dois tiverem CPFs diferentes. */
    @Transactional(readOnly = true)
    public Optional<String> motivoDuplicidade(AssistidoRequestDTO dto, Long idEmEdicao) {
        if (StringUtils.hasText(dto.cpf())) {
            Optional<Assistido> mesmoCpf = assistidoRepository.findByCpf(dto.cpf())
                    .filter(a -> !a.getAssistidoId().equals(idEmEdicao));
            if (mesmoCpf.isPresent()) {
                return Optional.of("CPF ja cadastrado para o assistido " + mesmoCpf.get().getNomeCompleto());
            }
        }

        boolean mesmaPessoa = assistidoRepository
                .findByNomeCompletoIgnoreCaseAndDataNascimento(dto.nomeCompleto(), dto.dataNascimento())
                .stream()
                .filter(a -> !a.getAssistidoId().equals(idEmEdicao))
                .anyMatch(a -> !cpfsDiferentes(a.getCpf(), dto.cpf()));
        if (mesmaPessoa) {
            return Optional.of("Ja existe assistido com mesmo nome e data de nascimento");
        }
        return Optional.empty();
    }

    static boolean cpfsDiferentes(String cpf, String outroCpf) {
        return StringUtils.hasText(cpf) && StringUtils.hasText(outroCpf) && !cpf.equals(outroCpf);
    }

    private void verificarDuplicidade(AssistidoRequestDTO dto, Long idEmEdicao) {
        motivoDuplicidade(dto, idEmEdicao).ifPresent(motivo -> {
            throw new RegraNegocioException(motivo);
        });
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
        Responsavel responsavel = responsavelMapper.paraNovaEntidade(dto.paraResponsavelRequest());
        return responsavelRepository.save(responsavel);
    }

    /**
     * Problema de saúde / PCD marcados exigem a descrição ("Qual?"). Quando o
     * indicador vem desmarcado, o mapper descarta a descrição.
     */
    private void validarDadosComplementares(AssistidoRequestDTO dto) {
        if (Boolean.TRUE.equals(dto.possuiProblemaSaude()) && !StringUtils.hasText(dto.problemaSaude())) {
            throw new RegraNegocioException("Informe qual e' o problema de saude do assistido");
        }
        if (Boolean.TRUE.equals(dto.pcd()) && !StringUtils.hasText(dto.deficiencia())) {
            throw new RegraNegocioException("Informe qual e' a deficiencia do assistido");
        }
        if (Boolean.TRUE.equals(dto.recebeProgramaTransferencia())) {
            Set<ProgramaTransferencia> programas = dto.programasTransferencia();
            if (programas == null || programas.isEmpty()) {
                throw new RegraNegocioException("Informe qual programa de transferencia de renda a familia recebe");
            }
            if (programas.contains(ProgramaTransferencia.OUTRO) && !StringUtils.hasText(dto.programaTransferenciaOutro())) {
                throw new RegraNegocioException("Informe o nome do outro programa de transferencia de renda");
            }
        }
    }

    private static final int MAXIMO_DOCUMENTOS_IDENTIDADE = 1;

    /** Cadastro + RG/CPF (obrigatório) + foto de perfil (opcional), tudo na mesma transação. */
    public AssistidoResponseDTO cadastrarComArquivos(AssistidoRequestDTO dto, MultipartFile foto,
                                                     List<MultipartFile> documentos) {
        List<MultipartFile> docs = documentos == null ? List.of()
                : documentos.stream().filter(d -> d != null && !d.isEmpty()).toList();
        // valida antes de criar o assistido, pra não gastar insert/upload à toa
        if (docs.isEmpty()) {
            throw new ArquivoInvalidoException("Envie a foto do RG ou CPF do assistido.");
        }
        if (docs.size() > MAXIMO_DOCUMENTOS_IDENTIDADE) {
            throw new ArquivoInvalidoException("Envie apenas um arquivo do RG ou CPF.");
        }

        AssistidoResponseDTO criado = cadastrar(dto);
        Long id = criado.assistidoId();
        if (foto != null && !foto.isEmpty()) {
            arquivoService.salvar(EntidadeArquivo.ASSISTIDO, id, CategoriaArquivo.FOTO_PERFIL, foto);
        }
        for (MultipartFile documento : docs) {
            arquivoService.salvar(EntidadeArquivo.ASSISTIDO, id, CategoriaArquivo.DOCUMENTO_IDENTIDADE, documento);
        }
        return criado;
    }
}
