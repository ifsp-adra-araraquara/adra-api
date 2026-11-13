package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ArquivarRegistroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PendenciasDisciplinaresDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.DocumentoDevolvidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.FichaDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.GerarPelaRecomendacaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroCriadoDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroVariosRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResolverConsequenciaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResumoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.SinalizacaoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ZerarContagemRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.OrigemRegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Arquivo;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.RegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.MotivoDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.RegistroDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.RegistroDisciplinarSpecification;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
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
 * FT-22: a cada 3 registros ativos do mesmo nível, o registro que completou
 * os 3 fica com a consequência do nível pendente (ver NivelDisciplinar):
 *   3ª ocorrência  -> recomenda advertência escrita
 *   3ª advertência -> recomenda suspensão de 3 dias
 *   3ª suspensão   -> perde o próximo passeio
 *   3ª de uniforme -> retorno com o responsável
 * Nada é gerado sozinho: o Coordenador gera o registro pela recomendação
 * (gerarPelaRecomendacao) ou dispensa (resolverConsequencia).
 *
 * Arquivar um registro não desfaz consequências já resolvidas; só tira o
 * registro das contagens dali para frente.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DisciplinarService {


    private final RegistroDisciplinarRepository registroRepository;
    private final MotivoDisciplinarRepository motivoRepository;
    private final AssistidoRepository assistidoRepository;
    private final TurmaRepository turmaRepository;
    private final ArquivoRepository arquivoRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final FaltaPorSuspensaoService faltaPorSuspensaoService;
    private final PeriodoSuspensaoService periodoSuspensaoService;

    // ------------------------------------------------------------------
    // Motivos
    // ------------------------------------------------------------------

    @Cacheable(CacheConfig.REFERENCIAS)
    @Transactional(readOnly = true)
    public List<MotivoDisciplinarResponseDTO> listarMotivos(boolean incluirInativos) {
        List<MotivoDisciplinar> motivos = incluirInativos
                ? motivoRepository.findAllByOrderByNivelAscNomeAsc()
                : motivoRepository.findByAtivoTrueOrderByNivelAscNomeAsc();
        return motivos.stream().map(MotivoDisciplinarResponseDTO::de).toList();
    }

    @CacheEvict(cacheNames = CacheConfig.REFERENCIAS, allEntries = true)
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
    @CacheEvict(cacheNames = CacheConfig.REFERENCIAS, allEntries = true)
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
     * Registra um fato e marca a recomendação, se completou 3 do nível.
     * Devolve o registro lançado e a recomendação pendente do assistido (para o front avisar).
     */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public RegistroCriadoDTO registrar(Long assistidoId, RegistroDisciplinarRequestDTO dto) {
        Assistido assistido = buscarAssistidoAtivo(assistidoId);

        MotivoDisciplinar motivo = null;
        String motivoTexto = null;
        NivelDisciplinar nivel;
        if (dto.motivoId() != null) {
            motivo = buscarMotivo(dto.motivoId());
            if (!motivo.isAtivo()) {
                throw new RegraNegocioException("O motivo \"" + motivo.getNome() + "\" está inativo.");
            }
            nivel = motivo.getNivel();
        } else {
            // E-mail da ADRA de 11/11: só os 3 níveis, com o motivo escrito à mão.
            if (dto.nivel() == null) {
                throw new RegraNegocioException("Informe se é ocorrência, advertência ou suspensão.");
            }
            if (dto.nivel() == NivelDisciplinar.UNIFORME) {
                throw new RegraNegocioException("Escolha ocorrência, advertência ou suspensão.");
            }
            if (dto.motivo() == null || dto.motivo().isBlank()) {
                throw new RegraNegocioException("Informe o motivo.");
            }
            nivel = dto.nivel();
            motivoTexto = dto.motivo().trim();
        }

        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();
        if (usuario.getNivelPermissao().getNome() == NomeNivelPermissao.EDUCADOR_SOCIAL
                && nivel != NivelDisciplinar.OCORRENCIA) {
            throw new AcessoNegadoException("O Educador Social só registra ocorrência.");
        }

        Turma turma = dto.turmaId() == null ? null : turmaRepository.findById(dto.turmaId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Grupo não encontrado: id " + dto.turmaId()));

        LocalDate inicio = null;
        LocalDate fim = null;
        if (nivel == NivelDisciplinar.SUSPENSAO) {
            inicio = dto.suspensaoInicio() != null ? dto.suspensaoInicio()
                    : periodoSuspensaoService.sugerir(assistido, dto.data()).inicio();
            fim = dto.suspensaoFim() != null ? dto.suspensaoFim() : periodoSuspensaoService.fimContandoDe(assistido, inicio);
            validarPeriodo(inicio, fim);
        }

        RegistroDisciplinar registro = salvarRegistro(assistido, motivo, motivoTexto, nivel,
                OrigemRegistroDisciplinar.MANUAL, null, dto.data(), dto.descricao().trim(), turma, inicio, fim,
                usuario);
        marcarRecomendacao(registro);
        if (inicio != null) {
            faltaPorSuspensaoService.sincronizar(assistido, inicio, fim, usuario); // FT-13: suspensão retroativa
        }
        return montarRespostaERecalcular(registro);
    }

    /** Um fato com vários atendidos: um registro por atendido, tudo ou nada (mesma transação). */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public List<RegistroCriadoDTO> registrarVarios(RegistroVariosRequestDTO dto) {
        if (dto.assistidoIds().stream().distinct().count() != dto.assistidoIds().size()) {
            throw new RegraNegocioException("Há atendidos repetidos na lista.");
        }
        dto.assistidoIds().forEach(this::buscarAssistidoAtivo);
        RegistroDisciplinarRequestDTO registro = new RegistroDisciplinarRequestDTO(
                dto.nivel(), dto.motivo(), dto.motivoId(), dto.data(), dto.descricao(), null, null, null);
        return dto.assistidoIds().stream().map(assistidoId -> registrar(assistidoId, registro)).toList();
    }

    /**
     * Só o Coordenador (ver DisciplinarController). Gera a advertência ou a
     * suspensão que o registro de origem recomendou e dá a recomendação por resolvida.
     */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public RegistroCriadoDTO gerarPelaRecomendacao(Long registroOrigemId, GerarPelaRecomendacaoRequestDTO dto) {
        RegistroDisciplinar origem = buscarRegistroAtivo(registroOrigemId);
        NivelDisciplinar nivel = origem.getConsequencia() == null ? null : origem.getConsequencia().nivelRecomendado();
        if (nivel == null) {
            throw new RegraNegocioException("Este registro não recomenda gerar advertência ou suspensão.");
        }
        if (!origem.temConsequenciaPendente()) {
            throw new RegraNegocioException("Esta recomendação já foi resolvida.");
        }

        LocalDate inicio = null;
        LocalDate fim = null;
        if (nivel == NivelDisciplinar.SUSPENSAO) {
            inicio = dto.suspensaoInicio() != null ? dto.suspensaoInicio()
                    : periodoSuspensaoService.sugerir(origem.getAssistido(), dto.data()).inicio();
            fim = dto.suspensaoFim() != null ? dto.suspensaoFim()
                    : periodoSuspensaoService.fimContandoDe(origem.getAssistido(), inicio);
            validarPeriodo(inicio, fim);
        }

        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();
        RegistroDisciplinar registro = salvarRegistro(origem.getAssistido(), null, null, nivel,
                OrigemRegistroDisciplinar.RECOMENDACAO, origem, dto.data(), dto.descricao().trim(), null,
                inicio, fim, usuario);
        marcarRecomendacao(registro);
        if (inicio != null) {
            faltaPorSuspensaoService.sincronizar(origem.getAssistido(), inicio, fim, usuario);
        }

        origem.setConsequenciaResolvidaEm(dto.data());
        origem.setConsequenciaResolvidaPor(usuario);
        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registroOrigemId,
                AcaoSistema.EDITAR, null,
                Map.of("consequencia", origem.getConsequencia().name(), "resolvidaEm", dto.data().toString(),
                        "registroGeradoId", registro.getRegistroId()),
                "Recomendação disciplinar gerada");
        return montarRespostaERecalcular(registro);
    }

    private RegistroCriadoDTO montarRespostaERecalcular(RegistroDisciplinar registro) {
        Assistido assistido = registro.getAssistido();
        RegistroDisciplinarResponseDTO recomendacao = registroRepository
                .findFirstByAssistidoAndArquivadoFalseAndConsequenciaIsNotNullAndConsequenciaResolvidaEmIsNullOrderByRegistroIdDesc(assistido)
                .map(RegistroDisciplinarResponseDTO::de)
                .orElse(null);
        // Monta a resposta antes: o recálculo limpa o contexto de persistência.
        RegistroCriadoDTO resposta = new RegistroCriadoDTO(RegistroDisciplinarResponseDTO.de(registro), recomendacao);
        assistidoRepository.recalcularContadoresDisciplinares(assistido.getAssistidoId());
        return resposta;
    }

    private RegistroDisciplinar salvarRegistro(Assistido assistido, MotivoDisciplinar motivo, String motivoTexto,
                                               NivelDisciplinar nivel,
                                               OrigemRegistroDisciplinar origem, RegistroDisciplinar registroOrigem,
                                               LocalDate data, String descricao, Turma turma,
                                               LocalDate suspensaoInicio, LocalDate suspensaoFim,
                                               Usuario usuario) {
        int sequencia = (int) registroRepository.countByAssistidoAndNivelAndArquivadoFalse(assistido, nivel) + 1;

        RegistroDisciplinar registro = RegistroDisciplinar.builder()
                .assistido(assistido)
                .motivo(motivo)
                .motivoTexto(motivoTexto)
                .nivel(nivel)
                .origem(origem)
                .registroOrigem(registroOrigem)
                .sequencia(sequencia)
                .dataRegistro(data)
                .descricao(descricao)
                .turma(turma)
                .suspensaoInicio(suspensaoInicio)
                .suspensaoFim(suspensaoFim)
                .documentoStatus(nivel.geraDocumento() ? StatusDocumentoDisciplinar.PENDENTE_ASSINATURA : null)
                .registradoPor(usuario)
                .build();
        registro = registroRepository.save(registro);

        // Sem a descrição: dado sensível (DT-01), fica só no registro.
        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registro.getRegistroId(),
                AcaoSistema.CRIAR, null,
                Map.of("assistidoId", assistido.getAssistidoId(), "nivel", nivel.name(), "origem", origem.name(),
                        "sequencia", sequencia),
                origem == OrigemRegistroDisciplinar.RECOMENDACAO
                        ? "Registro disciplinar gerado por recomendação"
                        : "Registro disciplinar");
        return registro;
    }

    /** Se o registro completou 3 do seu nível, deixa a consequência do nível pendente. Não cria registro. */
    private static void marcarRecomendacao(RegistroDisciplinar registro) {
        if (registro.getSequencia() % NivelDisciplinar.LIMITE_PROGRESSAO == 0) {
            registro.setConsequencia(registro.getNivel().consequencia());
        }
    }

    // ------------------------------------------------------------------
    // Ações sobre um registro
    // ------------------------------------------------------------------

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
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
                        "O arquivo precisa ser um documento disciplinar assinado deste atendido.");
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

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public RegistroDisciplinarResponseDTO alterarPeriodoSuspensao(Long registroId, PeriodoSuspensaoRequestDTO dto) {
        RegistroDisciplinar registro = buscarRegistroAtivo(registroId);
        if (registro.getNivel() != NivelDisciplinar.SUSPENSAO) {
            throw new RegraNegocioException("Só registros de suspensão têm período.");
        }
        validarPeriodo(dto.inicio(), dto.fim());

        LocalDate inicioAntes = registro.getSuspensaoInicio();
        LocalDate fimAntes = registro.getSuspensaoFim();
        Map<String, Object> anterior = Map.of("inicio", inicioAntes.toString(), "fim", fimAntes.toString());
        registro.setSuspensaoInicio(dto.inicio());
        registro.setSuspensaoFim(dto.fim());
        // FT-13: acerta as chamadas do período antigo e do novo (encurtar devolve à Falta).
        faltaPorSuspensaoService.sincronizar(registro.getAssistido(),
                inicioAntes.isBefore(dto.inicio()) ? inicioAntes : dto.inicio(),
                fimAntes.isAfter(dto.fim()) ? fimAntes : dto.fim(),
                usuarioAutenticadoService.getUsuarioAtual());

        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registroId, AcaoSistema.EDITAR,
                anterior, Map.of("inicio", dto.inicio().toString(), "fim", dto.fim().toString()),
                "Alteração do período de suspensão");
        return RegistroDisciplinarResponseDTO.de(registro);
    }

    /** Conversa com o responsável (3ª de uniforme) ou passeio perdido (3ª suspensão). */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
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
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public RegistroDisciplinarResponseDTO arquivar(Long registroId, ArquivarRegistroRequestDTO dto) {
        RegistroDisciplinar registro = buscarRegistroAtivo(registroId);
        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();
        marcarArquivado(registro, dto.motivo().trim(), usuario);
        sincronizarFaltasSeSuspensao(registro, usuario);

        Long assistidoId = registro.getAssistido().getAssistidoId();
        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "registro_disciplinar", registroId, AcaoSistema.EXCLUIR,
                Map.of("arquivado", false), Map.of("arquivado", true, "assistidoId", assistidoId),
                "Registro disciplinar arquivado");

        RegistroDisciplinarResponseDTO resposta = RegistroDisciplinarResponseDTO.de(registro);
        assistidoRepository.recalcularContadoresDisciplinares(assistidoId);
        return resposta;
    }

    /**
     * FT-24: só o Coordenador. Arquiva de uma vez os registros ativos do
     * assistido (de um nível ou de todos), então a próxima ocorrência volta a
     * ser a 1ª. O histórico continua visível como arquivado.
     */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public FichaDisciplinarDTO zerarContagem(Long assistidoId, ZerarContagemRequestDTO dto) {
        Assistido assistido = buscarAssistido(assistidoId);
        List<RegistroDisciplinar> ativos = registroRepository.findByAssistidoAndArquivadoFalse(assistido).stream()
                .filter(r -> dto.nivel() == null || r.getNivel() == dto.nivel())
                .toList();
        if (ativos.isEmpty()) {
            throw new RegraNegocioException("Não há registros ativos para zerar.");
        }

        String motivo = dto.motivo().trim();
        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();
        ativos.forEach(r -> marcarArquivado(r, motivo, usuario));
        ativos.forEach(r -> sincronizarFaltasSeSuspensao(r, usuario));

        auditoriaService.registrar(ModuloSistema.DISCIPLINA, "assistido", assistidoId, AcaoSistema.EXCLUIR, null,
                Map.of("nivel", dto.nivel() == null ? "TODOS" : dto.nivel().name(),
                        "registrosArquivados", ativos.size(),
                        "registroIds", ativos.stream().map(RegistroDisciplinar::getRegistroId).toList()),
                "Contagem disciplinar zerada");

        assistidoRepository.recalcularContadoresDisciplinares(assistidoId);
        return buscarFicha(assistidoId);
    }

    /** FT-13: suspensão arquivada deixa de justificar as faltas do período (voltam a Falta). */
    /** O que o encerramento do ano vai arquivar e o que ainda está pendente nesses registros. */
    public record PreviaArquivamentoAno(int registros, int documentosPendentes, int consequenciasPendentes) {
    }

    @Transactional(readOnly = true)
    public PreviaArquivamentoAno previaArquivamentoDoAno(int ano) {
        List<RegistroDisciplinar> registros = registroRepository.findAtivosAteOFimDoAno(LocalDate.of(ano, 12, 31));
        return new PreviaArquivamentoAno(
                registros.size(),
                (int) registros.stream().filter(r -> r.getDocumentoStatus() == StatusDocumentoDisciplinar.PENDENTE_ASSINATURA).count(),
                (int) registros.stream().filter(RegistroDisciplinar::temConsequenciaPendente).count());
    }

    /**
     * Encerramento do ano (pedido da ADRA de 13/11/2026): a contagem recomeça no ano
     * seguinte. Arquiva — não exclui — e não mexe nas faltas da chamada: o que já
     * aconteceu no ano continua igual no histórico e na frequência.
     *
     * @return quantos registros foram arquivados
     */
    public int arquivarNoEncerramentoDoAno(int ano, Usuario usuario) {
        List<RegistroDisciplinar> registros = registroRepository.findAtivosAteOFimDoAno(LocalDate.of(ano, 12, 31));
        String motivo = "Encerramento do ano de " + ano;
        registros.forEach(r -> marcarArquivado(r, motivo, usuario));
        registroRepository.flush();
        registros.stream().map(r -> r.getAssistido().getAssistidoId()).distinct()
                .forEach(assistidoRepository::recalcularContadoresDisciplinares);
        return registros.size();
    }

    private void sincronizarFaltasSeSuspensao(RegistroDisciplinar registro, Usuario usuario) {
        if (registro.getNivel() == NivelDisciplinar.SUSPENSAO) {
            faltaPorSuspensaoService.sincronizar(registro.getAssistido(),
                    registro.getSuspensaoInicio(), registro.getSuspensaoFim(), usuario);
        }
    }

    private static void marcarArquivado(RegistroDisciplinar registro, String motivo, Usuario usuario) {
        registro.setArquivado(true);
        registro.setArquivadoMotivo(motivo);
        registro.setArquivadoEm(OffsetDateTime.now());
        registro.setArquivadoPor(usuario);
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
                                                            boolean somentePendencias, boolean somenteRecomendacoes,
                                                            boolean incluirArquivados, int pagina, int tamanho) {
        Sort ordem = Sort.by(Sort.Order.desc("dataRegistro"), Sort.Order.desc("registroId"));
        Page<RegistroDisciplinar> page = registroRepository.findAll(
                RegistroDisciplinarSpecification.comFiltros(busca, nivel, somentePendencias, somenteRecomendacoes,
                        incluirArquivados),
                PageRequest.of(pagina, tamanho, ordem));
        return PaginaDTO.de(page.map(RegistroDisciplinarResponseDTO::de));
    }

    @Transactional(readOnly = true)
    public PendenciasDisciplinaresDTO buscarPendencias() {
        return new PendenciasDisciplinaresDTO(
                registroRepository.contarAssistidosSuspensosEm(LocalDate.now()),
                registroRepository.contarAssistidosComConsequenciaPendente(ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL),
                registroRepository.contarAssistidosComConsequenciaPendente(ConsequenciaDisciplinar.PERDE_PROXIMO_PASSEIO),
                registroRepository.countByArquivadoFalseAndDocumentoStatus(StatusDocumentoDisciplinar.PENDENTE_ASSINATURA),
                registroRepository.contarAssistidosComConsequenciaPendente(ConsequenciaDisciplinar.RECOMENDA_ADVERTENCIA),
                registroRepository.contarAssistidosComConsequenciaPendente(ConsequenciaDisciplinar.RECOMENDA_SUSPENSAO));
    }

    @Transactional(readOnly = true)
    public RegistroDisciplinarResponseDTO buscarRegistro(Long registroId) {
        RegistroDisciplinar registro = buscarRegistroEntidade(registroId);
        PeriodoSuspensaoDTO periodo = registro.getNivel() == NivelDisciplinar.SUSPENSAO
                ? periodoSuspensaoService.periodo(registro.getAssistido(), registro.getSuspensaoInicio(), registro.getSuspensaoFim())
                : null;
        return RegistroDisciplinarResponseDTO.de(registro, periodo);
    }

    /** Período padrão de uma suspensão pelo fato nesta data, em dias de atividade do grupo (telas de registro). */
    @Transactional(readOnly = true)
    public PeriodoSuspensaoDTO sugerirPeriodoSuspensao(Long assistidoId, LocalDate dataDoFato) {
        return periodoSuspensaoService.sugerir(buscarAssistido(assistidoId), dataDoFato);
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
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Atendido não encontrado: id " + assistidoId));
    }

    private Assistido buscarAssistidoAtivo(Long assistidoId) {
        Assistido assistido = buscarAssistido(assistidoId);
        if (assistido.getStatus() != StatusGeral.ATIVO) {
            throw new RegraNegocioException(assistido.getNomeCompleto()
                    + " não está ativo: só é possível registrar ocorrência de atendido ativo.");
        }
        return assistido;
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
