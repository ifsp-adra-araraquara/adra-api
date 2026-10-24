package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ArquivarRegistroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PendenciasDisciplinaresDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.DocumentoDevolvidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.FichaDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResolverConsequenciaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResumoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.SinalizacaoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.OrigemRegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Arquivo;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.RegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.MotivoDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.RegistroDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.RegistroDisciplinarSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FT-04: regulamento disciplinar da ADRA (cartazes "Disciplina ADRA" e
 * comunicado de uso do uniforme).
 *
 * A cada 3 registros ativos do mesmo nível, o nível aplica sua consequência
 * (ver NivelDisciplinar):
 *   3ª ocorrência  -> gera advertência escrita
 *   3ª advertência -> gera suspensão de 3 dias
 *   3ª suspensão   -> perde o próximo passeio (pendente)
 *   3ª de uniforme -> retorno com o responsável (pendente)
 * Advertência e suspensão geradas entram na contagem do seu nível, então a
 * progressão encadeia na mesma transação (a 9ª ocorrência pode gerar a 3ª
 * advertência, que gera uma suspensão).
 *
 * Arquivar um registro não desfaz consequências já aplicadas; só tira o
 * registro das contagens dali para frente.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DisciplinarService {

    /** "Suspenso por três dias": do dia do fato até 2 dias depois, quando o período não é informado. */
    private static final int DIAS_SUSPENSAO_PADRAO = 3;

    private final RegistroDisciplinarRepository registroRepository;
    private final MotivoDisciplinarRepository motivoRepository;
    private final AssistidoRepository assistidoRepository;
    private final AulaRepository aulaRepository;
    private final ArquivoRepository arquivoRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    // ------------------------------------------------------------------
    // Motivos
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MotivoDisciplinarResponseDTO> listarMotivos(boolean incluirInativos) {
        List<MotivoDisciplinar> motivos = incluirInativos
                ? motivoRepository.findAllByOrderByNivelAscNomeAsc()
                : motivoRepository.findByAtivoTrueOrderByNivelAscNomeAsc();
        return motivos.stream().map(MotivoDisciplinarResponseDTO::de).toList();
    }

    public MotivoDisciplinarResponseDTO criarMotivo(MotivoDisciplinarRequestDTO dto) {
        MotivoDisciplinar motivo = MotivoDisciplinar.builder()
                .nome(dto.nome().trim())
                .nivel(dto.nivel())
                .ativo(dto.ativo() == null || dto.ativo())
                .build();
        motivo = motivoRepository.save(motivo);

        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "motivo_disciplinar", motivo.getMotivoId(),
                AcaoSistema.CRIAR, null, Map.of("nome", motivo.getNome(), "nivel", motivo.getNivel().name()),
                "Cadastro de motivo disciplinar");
        return MotivoDisciplinarResponseDTO.de(motivo);
    }

    /** Mudar o nível de um motivo não altera os registros antigos (o nível fica gravado no registro). */
    public MotivoDisciplinarResponseDTO atualizarMotivo(Long motivoId, MotivoDisciplinarRequestDTO dto) {
        MotivoDisciplinar motivo = buscarMotivo(motivoId);
        Map<String, Object> anterior = Map.of("nome", motivo.getNome(), "nivel", motivo.getNivel().name(),
                "ativo", motivo.isAtivo());

        motivo.setNome(dto.nome().trim());
        motivo.setNivel(dto.nivel());
        if (dto.ativo() != null) {
            motivo.setAtivo(dto.ativo());
        }

        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "motivo_disciplinar", motivoId, AcaoSistema.EDITAR,
                anterior, Map.of("nome", motivo.getNome(), "nivel", motivo.getNivel().name(), "ativo", motivo.isAtivo()),
                "Alteração de motivo disciplinar");
        return MotivoDisciplinarResponseDTO.de(motivo);
    }

    // ------------------------------------------------------------------
    // Registro e progressão
    // ------------------------------------------------------------------

    /**
     * Registra um fato e aplica a progressão. Devolve o registro lançado e,
     * em seguida, os que a progressão gerou (para o front avisar).
     */
    public List<RegistroDisciplinarResponseDTO> registrar(Long assistidoId, RegistroDisciplinarRequestDTO dto) {
        Assistido assistido = buscarAssistido(assistidoId);
        if (assistido.getStatus() != StatusGeral.ATIVO) {
            throw new RegraNegocioException("Só é possível registrar ocorrência de assistido ativo.");
        }

        MotivoDisciplinar motivo = buscarMotivo(dto.motivoId());
        if (!motivo.isAtivo()) {
            throw new RegraNegocioException("O motivo \"" + motivo.getNome() + "\" está inativo.");
        }

        Aula aula = dto.aulaId() == null ? null : aulaRepository.findById(dto.aulaId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + dto.aulaId()));

        LocalDate inicio = null;
        LocalDate fim = null;
        if (motivo.getNivel() == NivelDisciplinar.SUSPENSAO) {
            inicio = dto.suspensaoInicio() != null ? dto.suspensaoInicio() : dto.data();
            fim = dto.suspensaoFim() != null ? dto.suspensaoFim() : inicio.plusDays(DIAS_SUSPENSAO_PADRAO - 1);
            validarPeriodo(inicio, fim);
        }

        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();
        List<RegistroDisciplinar> criados = new ArrayList<>();

        RegistroDisciplinar registro = salvarRegistro(assistido, motivo, motivo.getNivel(),
                OrigemRegistroDisciplinar.MANUAL, null, dto.data(), dto.descricao().trim(), aula, inicio, fim,
                usuario, criados);
        aplicarProgressao(registro, usuario, criados);

        // Monta a resposta antes: o recálculo limpa o contexto de persistência.
        List<RegistroDisciplinarResponseDTO> resposta = criados.stream().map(RegistroDisciplinarResponseDTO::de).toList();
        assistidoRepository.recalcularContadoresDisciplinares(assistidoId);
        return resposta;
    }

    private RegistroDisciplinar salvarRegistro(Assistido assistido, MotivoDisciplinar motivo, NivelDisciplinar nivel,
                                               OrigemRegistroDisciplinar origem, RegistroDisciplinar registroOrigem,
                                               LocalDate data, String descricao, Aula aula,
                                               LocalDate suspensaoInicio, LocalDate suspensaoFim,
                                               Usuario usuario, List<RegistroDisciplinar> criados) {
        int sequencia = (int) registroRepository.countByAssistidoAndNivelAndArquivadoFalse(assistido, nivel) + 1;

        RegistroDisciplinar registro = RegistroDisciplinar.builder()
                .assistido(assistido)
                .motivo(motivo)
                .nivel(nivel)
                .origem(origem)
                .registroOrigem(registroOrigem)
                .sequencia(sequencia)
                .dataRegistro(data)
                .descricao(descricao)
                .aula(aula)
                .suspensaoInicio(suspensaoInicio)
                .suspensaoFim(suspensaoFim)
                .documentoStatus(nivel.geraDocumento() ? StatusDocumentoDisciplinar.PENDENTE_ASSINATURA : null)
                .registradoPor(usuario)
                .build();
        registro = registroRepository.save(registro);
        criados.add(registro);

        // Sem a descrição: dado sensível (DT-01), fica só no registro.
        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registro.getRegistroId(),
                AcaoSistema.CRIAR, null,
                Map.of("assistidoId", assistido.getAssistidoId(), "nivel", nivel.name(), "origem", origem.name(),
                        "sequencia", sequencia),
                origem == OrigemRegistroDisciplinar.PROGRESSAO
                        ? "Registro disciplinar gerado por progressão"
                        : "Registro disciplinar");
        return registro;
    }

    /** Se o registro completou 3 do seu nível, aplica a consequência (e encadeia, se ela gerar outro registro). */
    private void aplicarProgressao(RegistroDisciplinar registro, Usuario usuario, List<RegistroDisciplinar> criados) {
        if (registro.getSequencia() % NivelDisciplinar.LIMITE_PROGRESSAO != 0) {
            return;
        }

        ConsequenciaDisciplinar consequencia = registro.getNivel().consequencia();
        registro.setConsequencia(consequencia);

        switch (consequencia) {
            case ADVERTENCIA_GERADA -> {
                registro.setConsequenciaResolvidaEm(registro.getDataRegistro());
                RegistroDisciplinar advertencia = salvarRegistro(registro.getAssistido(), null,
                        NivelDisciplinar.ADVERTENCIA, OrigemRegistroDisciplinar.PROGRESSAO, registro,
                        registro.getDataRegistro(),
                        "Advertência escrita gerada automaticamente pela 3ª ocorrência registrada.",
                        null, null, null, usuario, criados);
                aplicarProgressao(advertencia, usuario, criados);
            }
            case SUSPENSAO_GERADA -> {
                registro.setConsequenciaResolvidaEm(registro.getDataRegistro());
                LocalDate inicio = registro.getDataRegistro();
                RegistroDisciplinar suspensao = salvarRegistro(registro.getAssistido(), null,
                        NivelDisciplinar.SUSPENSAO, OrigemRegistroDisciplinar.PROGRESSAO, registro,
                        registro.getDataRegistro(),
                        "Suspensão de 3 dias gerada automaticamente pela 3ª advertência registrada.",
                        null, inicio, inicio.plusDays(DIAS_SUSPENSAO_PADRAO - 1), usuario, criados);
                aplicarProgressao(suspensao, usuario, criados);
            }
            // PERDE_PROXIMO_PASSEIO e RETORNO_COM_RESPONSAVEL ficam pendentes até a coordenação resolver.
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // Ações sobre um registro
    // ------------------------------------------------------------------

    public RegistroDisciplinarResponseDTO marcarDocumentoDevolvido(Long registroId, DocumentoDevolvidoRequestDTO dto) {
        RegistroDisciplinar registro = buscarRegistroAtivo(registroId);
        if (registro.getDocumentoStatus() == null) {
            throw new RegraNegocioException("Este registro não tem documento para os pais assinarem.");
        }

        if (dto != null && dto.arquivoId() != null) {
            Arquivo arquivo = arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(dto.arquivoId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Arquivo não encontrado: id " + dto.arquivoId()));
            boolean doAssistido = arquivo.getEntidadeTipo() == EntidadeArquivo.ASSISTIDO
                    && Objects.equals(arquivo.getEntidadeId(), registro.getAssistido().getAssistidoId())
                    && arquivo.getCategoria() == CategoriaArquivo.DOCUMENTO_DISCIPLINAR_ASSINADO;
            if (!doAssistido) {
                throw new RegraNegocioException(
                        "O arquivo precisa ser um documento disciplinar assinado deste assistido.");
            }
            registro.setDocumentoArquivo(arquivo);
        }
        registro.setDocumentoStatus(StatusDocumentoDisciplinar.DEVOLVIDO_ASSINADO);

        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registroId, AcaoSistema.EDITAR,
                Map.of("documentoStatus", StatusDocumentoDisciplinar.PENDENTE_ASSINATURA.name()),
                Map.of("documentoStatus", StatusDocumentoDisciplinar.DEVOLVIDO_ASSINADO.name()),
                "Documento disciplinar devolvido assinado");
        return RegistroDisciplinarResponseDTO.de(registro);
    }

    public RegistroDisciplinarResponseDTO alterarPeriodoSuspensao(Long registroId, PeriodoSuspensaoRequestDTO dto) {
        RegistroDisciplinar registro = buscarRegistroAtivo(registroId);
        if (registro.getNivel() != NivelDisciplinar.SUSPENSAO) {
            throw new RegraNegocioException("Só registros de suspensão têm período.");
        }
        validarPeriodo(dto.inicio(), dto.fim());

        Map<String, Object> anterior = Map.of("inicio", registro.getSuspensaoInicio().toString(),
                "fim", registro.getSuspensaoFim().toString());
        registro.setSuspensaoInicio(dto.inicio());
        registro.setSuspensaoFim(dto.fim());

        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registroId, AcaoSistema.EDITAR,
                anterior, Map.of("inicio", dto.inicio().toString(), "fim", dto.fim().toString()),
                "Alteração do período de suspensão");
        return RegistroDisciplinarResponseDTO.de(registro);
    }

    /** Conversa com o responsável (3ª de uniforme) ou passeio perdido (3ª suspensão). */
    public RegistroDisciplinarResponseDTO resolverConsequencia(Long registroId, ResolverConsequenciaRequestDTO dto) {
        RegistroDisciplinar registro = buscarRegistroAtivo(registroId);
        if (!registro.temConsequenciaPendente()) {
            throw new RegraNegocioException("Este registro não tem consequência pendente.");
        }

        registro.setConsequenciaResolvidaEm(dto.data());
        registro.setConsequenciaObservacao(dto.observacao().trim());
        registro.setConsequenciaResolvidaPor(usuarioAutenticadoService.getUsuarioAtual());

        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registroId, AcaoSistema.EDITAR,
                null, Map.of("consequencia", registro.getConsequencia().name(), "resolvidaEm", dto.data().toString()),
                "Consequência disciplinar resolvida");
        return RegistroDisciplinarResponseDTO.de(registro);
    }

    /** Só o Coordenador (ver DisciplinarController). Não exclui e não desfaz consequências já aplicadas. */
    public RegistroDisciplinarResponseDTO arquivar(Long registroId, ArquivarRegistroRequestDTO dto) {
        RegistroDisciplinar registro = buscarRegistroAtivo(registroId);

        registro.setArquivado(true);
        registro.setArquivadoMotivo(dto.motivo().trim());
        registro.setArquivadoEm(OffsetDateTime.now());
        registro.setArquivadoPor(usuarioAutenticadoService.getUsuarioAtual());

        Long assistidoId = registro.getAssistido().getAssistidoId();
        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registroId, AcaoSistema.EXCLUIR,
                Map.of("arquivado", false), Map.of("arquivado", true, "assistidoId", assistidoId),
                "Registro disciplinar arquivado");

        RegistroDisciplinarResponseDTO resposta = RegistroDisciplinarResponseDTO.de(registro);
        assistidoRepository.recalcularContadoresDisciplinares(assistidoId);
        return resposta;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FichaDisciplinarDTO buscarFicha(Long assistidoId) {
        Assistido assistido = buscarAssistido(assistidoId);
        List<RegistroDisciplinar> registros = registroRepository.findHistorico(assistido);
        LocalDate hoje = LocalDate.now();

        List<RegistroDisciplinar> ativos = registros.stream().filter(r -> !r.isArquivado()).toList();
        long ocorrencias = contar(ativos, NivelDisciplinar.OCORRENCIA);
        long advertencias = contar(ativos, NivelDisciplinar.ADVERTENCIA);
        long suspensoes = contar(ativos, NivelDisciplinar.SUSPENSAO);
        long uniforme = contar(ativos, NivelDisciplinar.UNIFORME);

        LocalDate suspensoAte = ativos.stream()
                .filter(r -> r.getNivel() == NivelDisciplinar.SUSPENSAO && !r.getSuspensaoFim().isBefore(hoje))
                .map(RegistroDisciplinar::getSuspensaoFim)
                .max(Comparator.naturalOrder())
                .orElse(null);

        ResumoDisciplinarDTO resumo = new ResumoDisciplinarDTO(
                ocorrencias, advertencias, suspensoes, uniforme,
                faltamParaProxima(ocorrencias), faltamParaProxima(advertencias),
                faltamParaProxima(suspensoes), faltamParaProxima(uniforme),
                suspensoAte,
                temPendente(ativos, ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL),
                temPendente(ativos, ConsequenciaDisciplinar.PERDE_PROXIMO_PASSEIO),
                ativos.stream().filter(r -> r.getDocumentoStatus() == StatusDocumentoDisciplinar.PENDENTE_ASSINATURA).count()
        );

        return new FichaDisciplinarDTO(resumo, registros.stream().map(RegistroDisciplinarResponseDTO::de).toList());
    }

    /** Lista geral da tela Disciplinar, mais recente primeiro. */
    @Transactional(readOnly = true)
    public PaginaDTO<RegistroDisciplinarResponseDTO> listar(String busca, NivelDisciplinar nivel,
                                                            boolean somentePendencias, boolean incluirArquivados,
                                                            int pagina, int tamanho) {
        Sort ordem = Sort.by(Sort.Order.desc("dataRegistro"), Sort.Order.desc("registroId"));
        Page<RegistroDisciplinar> page = registroRepository.findAll(
                RegistroDisciplinarSpecification.comFiltros(busca, nivel, somentePendencias, incluirArquivados),
                PageRequest.of(pagina, tamanho, ordem));
        return PaginaDTO.de(page.map(RegistroDisciplinarResponseDTO::de));
    }

    @Transactional(readOnly = true)
    public PendenciasDisciplinaresDTO buscarPendencias() {
        return new PendenciasDisciplinaresDTO(
                registroRepository.contarAssistidosSuspensosEm(LocalDate.now()),
                registroRepository.contarAssistidosComConsequenciaPendente(ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL),
                registroRepository.contarAssistidosComConsequenciaPendente(ConsequenciaDisciplinar.PERDE_PROXIMO_PASSEIO),
                registroRepository.countByArquivadoFalseAndDocumentoStatus(StatusDocumentoDisciplinar.PENDENTE_ASSINATURA));
    }

    @Transactional(readOnly = true)
    public RegistroDisciplinarResponseDTO buscarRegistro(Long registroId) {
        return RegistroDisciplinarResponseDTO.de(buscarRegistroEntidade(registroId));
    }

    /**
     * Sinalizações (suspenso na data, retorno com responsável pendente, perde
     * o próximo passeio) de vários assistidos de uma vez — chamada e
     * listagem. Só devolve quem tem alguma.
     */
    @Transactional(readOnly = true)
    public List<SinalizacaoDisciplinarDTO> buscarSinalizacoes(Collection<Long> assistidoIds, LocalDate data) {
        if (assistidoIds == null || assistidoIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RegistroDisciplinar>> porAssistido = new LinkedHashMap<>();
        for (RegistroDisciplinar r : registroRepository.findCandidatosASinalizacao(assistidoIds)) {
            porAssistido.computeIfAbsent(r.getAssistido().getAssistidoId(), k -> new ArrayList<>()).add(r);
        }

        List<SinalizacaoDisciplinarDTO> resultado = new ArrayList<>();
        porAssistido.forEach((assistidoId, registros) -> {
            LocalDate suspensoAte = registros.stream()
                    .filter(r -> r.suspensoEm(data))
                    .map(RegistroDisciplinar::getSuspensaoFim)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            boolean retorno = temPendente(registros, ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL);
            boolean passeio = temPendente(registros, ConsequenciaDisciplinar.PERDE_PROXIMO_PASSEIO);
            if (suspensoAte != null || retorno || passeio) {
                resultado.add(new SinalizacaoDisciplinarDTO(assistidoId, suspensoAte, retorno, passeio));
            }
        });
        return resultado;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private static long contar(List<RegistroDisciplinar> registros, NivelDisciplinar nivel) {
        return registros.stream().filter(r -> r.getNivel() == nivel).count();
    }

    /** Quantos faltam para o próximo múltiplo de 3 (3, 2 ou 1). */
    private static int faltamParaProxima(long ativos) {
        return NivelDisciplinar.LIMITE_PROGRESSAO - (int) (ativos % NivelDisciplinar.LIMITE_PROGRESSAO);
    }

    private static boolean temPendente(List<RegistroDisciplinar> registros, ConsequenciaDisciplinar consequencia) {
        return registros.stream().anyMatch(r -> r.getConsequencia() == consequencia && r.temConsequenciaPendente());
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new RegraNegocioException("O fim da suspensão não pode ser antes do início.");
        }
    }

    private Assistido buscarAssistido(Long assistidoId) {
        return assistidoRepository.findById(assistidoId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + assistidoId));
    }

    private MotivoDisciplinar buscarMotivo(Long motivoId) {
        return motivoRepository.findById(motivoId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Motivo disciplinar não encontrado: id " + motivoId));
    }

    private RegistroDisciplinar buscarRegistroEntidade(Long registroId) {
        return registroRepository.findById(registroId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Registro disciplinar não encontrado: id " + registroId));
    }

    private RegistroDisciplinar buscarRegistroAtivo(Long registroId) {
        RegistroDisciplinar registro = buscarRegistroEntidade(registroId);
        if (registro.isArquivado()) {
            throw new RegraNegocioException("Este registro está arquivado.");
        }
        return registro;
    }
}
