package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.service.ArquivoService.AnexoNovo;
import adra.ifsp.edu.br.api.domain.dto.arquivo.ArquivoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.arquivo.UrlArquivoDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.AnexosPreCadastro;
import adra.ifsp.edu.br.api.domain.dto.precadastro.ObservacaoPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Mae;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.ResponsavelLegal;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResponseDTO.PossivelMesmoAtendido;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResumoDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.ReprovarPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.mapper.PreCadastroParaAssistido;
import adra.ifsp.edu.br.api.domain.model.Arquivo;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.MatriculaRepository;
import adra.ifsp.edu.br.api.domain.repository.PreCadastroRepository;
import adra.ifsp.edu.br.api.exception.ArquivoInvalidoException;
import adra.ifsp.edu.br.api.exception.AssistidoJaCadastradoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import adra.ifsp.edu.br.api.infra.TurnstileClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Pré-cadastro público (sem login). Grava num rascunho isolado; a equipe
 * revisa, edita e aprova (cria o assistido) ou reprova com motivo.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PreCadastroService {

    /** Mesmo IP não pode enviar outro pré-cadastro antes desse intervalo — mitiga spam/flood simples. */
    private static final Duration JANELA_LIMITE = Duration.ofMinutes(2);
    private static final int LIMITE_DIARIO_POR_IP = 20;
    private static final int LIMITE_DIARIO_GLOBAL = 180;
    /** Frente e verso. */
    private static final int MAXIMO_DOCUMENTOS = 2;

    private final PreCadastroRepository repository;
    private final AssistidoRepository assistidoRepository;
    private final AssistidoService assistidoService;
    private final PreCadastroParaAssistido paraAssistido;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final ArquivoService arquivoService;
    private final ArquivoRepository arquivoRepository;
    private final AnexosDoPreCadastro anexosDoPreCadastro;
    private final MatriculaRepository matriculaRepository;
    private final PeriodoMatriculaService periodoMatriculaService;
    private final MatriculaService matriculaService;
    private final TurnstileClient turnstile;

    private final Map<String, EnviosDoIp> enviosPorIp = new ConcurrentHashMap<>();

    private record EnviosDoIp(Instant ultimo, LocalDate dia, int totalNoDia) {
    }

    /** Devolve o id (protocolo para a família); null quando o honeypot pegou um robô. */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long registrarPublico(PreCadastroRequestDTO dto, AnexosPreCadastro anexos, String captcha, String ip) {
        // Honeypot: campo invisível pro humano: se veio preenchido, é robô — finge sucesso.
        if (dto.site() != null && !dto.site().isBlank()) {
            return null;
        }
        validarCondicionais(dto.dados());
        validarAnexos(anexos);
        if (!turnstile.tokenValido(captcha)) {
            throw new RegraNegocioException("Não foi possível confirmar que você não é um robô. Tente de novo.");
        }
        registrarEnvio(ip, Instant.now());
        validarLimiteGlobal();

        return arquivoService.enviarEGravar(EntidadeArquivo.PRE_CADASTRO, anexosNovos(anexos), enviados -> {
            PreCadastro registro = PreCadastro.builder()
                    .nomeCompleto(dto.nomeCompleto().trim())
                    .dataNascimento(dto.dataNascimento())
                    .cpf(dto.cpf())
                    .sexo(dto.sexo())
                    .email(StringUtils.hasText(dto.email()) ? dto.email().trim() : null)
                    .responsavelNome(nomeDoResponsavel(dto.dados()))
                    .responsavelTelefone(telefoneDoResponsavel(dto.dados()))
                    .dados(dto.dados())
                    .anoReferencia(periodoMatriculaService.anoDaInscricao())
                    .ipOrigem(ip)
                    .build();
            repository.save(registro);
            enviados.forEach(enviado -> arquivoService.registrarDoLinkPublico(registro.getPreCadastroId(), enviado));

            auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", registro.getPreCadastroId(),
                    AcaoSistema.CRIAR, null, Map.of("nomeCompleto", registro.getNomeCompleto()),
                    "Pré-cadastro recebido pelo link público");
            return registro.getPreCadastroId();
        });
    }

    private void validarLimiteGlobal() {
        OffsetDateTime inicioDoDia = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        if (repository.countByCriadoEmGreaterThanEqual(inicioDoDia) >= LIMITE_DIARIO_GLOBAL) {
            throw new RegraNegocioException("Limite de inscrições por hoje atingido. Tente amanhã ou procure a ADRA.");
        }
    }

    private void validarAnexos(AnexosPreCadastro anexos) {
        if (anexos.comprovanteEndereco() == null) {
            throw new ArquivoInvalidoException("Envie o comprovante de endereço.");
        }
        if (anexos.foto() == null) {
            throw new ArquivoInvalidoException("Envie a foto do atendido.");
        }
        if (anexos.documentosCrianca().isEmpty()) {
            throw new ArquivoInvalidoException("Envie o documento do atendido (RG ou CPF).");
        }
        if (anexos.documentosResponsavel().isEmpty()) {
            throw new ArquivoInvalidoException("Envie o documento do responsável (RG ou CPF).");
        }
        if (anexos.documentosCrianca().size() > MAXIMO_DOCUMENTOS || anexos.documentosResponsavel().size() > MAXIMO_DOCUMENTOS) {
            throw new ArquivoInvalidoException("Envie no máximo 2 arquivos por documento (frente e verso).");
        }
    }

    private List<AnexoNovo> anexosNovos(AnexosPreCadastro anexos) {
        List<AnexoNovo> novos = new ArrayList<>();
        novos.add(new AnexoNovo(CategoriaArquivo.DECLARACAO_RESIDENCIA, anexos.comprovanteEndereco()));
        if (anexos.foto() != null) {
            novos.add(new AnexoNovo(CategoriaArquivo.FOTO_PERFIL, anexos.foto()));
        }
        for (MultipartFile documento : anexos.documentosCrianca()) {
            novos.add(new AnexoNovo(CategoriaArquivo.DOCUMENTO_IDENTIDADE, documento));
        }
        for (MultipartFile documento : anexos.documentosResponsavel()) {
            novos.add(new AnexoNovo(CategoriaArquivo.DOCUMENTO_RESPONSAVEL, documento));
        }
        return novos;
    }

    /** O que a família deixou de enviar (a equipe pede na entrevista). */
    static List<String> anexosFaltando(Collection<CategoriaArquivo> enviados) {
        List<String> faltando = new ArrayList<>();
        if (!enviados.contains(CategoriaArquivo.FOTO_PERFIL)) faltando.add("Foto do atendido");
        if (!enviados.contains(CategoriaArquivo.DOCUMENTO_IDENTIDADE)) faltando.add("Documento do atendido");
        if (!enviados.contains(CategoriaArquivo.DECLARACAO_RESIDENCIA)) faltando.add("Comprovante de endereço");
        if (!enviados.contains(CategoriaArquivo.DOCUMENTO_RESPONSAVEL)) faltando.add("Documento do responsável");
        return faltando;
    }

    /** Regras do formulário que dependem de outra resposta ("se sim, qual?"). */
    void validarCondicionais(PreCadastroDadosDTO dados) {
        Mae mae = dados.mae();
        ResponsavelLegal legal = dados.responsavelLegal();
        if (mae == null && (legal == null || !StringUtils.hasText(legal.cpf()))) {
            throw new RegraNegocioException("Informe os dados da mãe ou o CPF do responsável legal.");
        }
        if (mae != null && mae.estadoCivil() == null && !StringUtils.hasText(mae.estadoCivilOutro())) {
            throw new RegraNegocioException("Informe o estado civil da mãe.");
        }
        if (!StringUtils.hasText(dados.enderecoFamilia().logradouro())) {
            throw new RegraNegocioException("Informe a rua do endereço da família.");
        }
        if (Boolean.TRUE.equals(dados.saude().possuiProblema()) && !StringUtils.hasText(dados.saude().descricao())) {
            throw new RegraNegocioException("Informe qual é o problema de saúde do atendido.");
        }
        if (Boolean.TRUE.equals(dados.transferencia().recebe())) {
            Set<ProgramaTransferencia> programas = dados.transferencia().programas();
            if (programas == null || programas.isEmpty()) {
                throw new RegraNegocioException("Marque qual programa de transferência de renda a família recebe.");
            }
            if (programas.contains(ProgramaTransferencia.OUTRO) && !StringUtils.hasText(dados.transferencia().outro())) {
                throw new RegraNegocioException("Informe o nome do outro programa de transferência de renda.");
            }
        }
        if (Boolean.FALSE.equals(dados.autorizacoes().irEmboraSozinho())
                && !StringUtils.hasText(dados.autorizacoes().quemBusca())) {
            throw new RegraNegocioException("Informe quem vem buscar o atendido.");
        }
    }

    /** 1 envio a cada 2 minutos e no máximo 20 por dia, por IP (em memória: a API roda numa instância só). */
    void registrarEnvio(String ip, Instant agora) {
        if (ip == null) {
            return;
        }
        LocalDate hoje = LocalDate.ofInstant(agora, ZoneId.systemDefault());
        EnviosDoIp anterior = enviosPorIp.get(ip);
        if (anterior != null && Duration.between(anterior.ultimo(), agora).compareTo(JANELA_LIMITE) < 0) {
            throw new RegraNegocioException("Aguarde um pouco antes de enviar outro pré-cadastro.");
        }
        int totalNoDia = anterior != null && anterior.dia().equals(hoje) ? anterior.totalNoDia() : 0;
        if (totalNoDia >= LIMITE_DIARIO_POR_IP) {
            throw new RegraNegocioException("Limite de inscrições por hoje atingido neste aparelho. Tente amanhã ou procure a ADRA.");
        }
        enviosPorIp.put(ip, new EnviosDoIp(agora, hoje, totalNoDia + 1));
    }

    private String nomeDoResponsavel(PreCadastroDadosDTO dados) {
        return dados.mae() != null ? dados.mae().nomeCompleto().trim() : dados.responsavelLegal().nomeCompleto().trim();
    }

    private String telefoneDoResponsavel(PreCadastroDadosDTO dados) {
        return dados.mae() != null ? dados.mae().telefone() : dados.responsavelLegal().telefone();
    }

    @Transactional(readOnly = true)
    /** {@code ano} null = todos os anos (ordem: em avaliação por chegada; os demais, mais recentes primeiro). */
    public List<PreCadastroResumoDTO> listar(StatusPreCadastro status, Integer ano) {
        List<PreCadastro> registros;
        if (ano == null) {
            registros = status.emAvaliacao()
                    ? repository.findByStatusOrderByCriadoEmAsc(status)
                    : repository.findByStatusOrderByCriadoEmDesc(status);
        } else {
            registros = status.emAvaliacao()
                    ? repository.findByStatusAndAnoReferenciaOrderByCriadoEmAsc(status, ano)
                    : repository.findByStatusAndAnoReferenciaOrderByCriadoEmDesc(status, ano);
        }
        Set<String> cpfs = registros.stream()
                .map(PreCadastro::getCpf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> cadastrados = status.emAvaliacao() && !cpfs.isEmpty()
                ? new HashSet<>(assistidoRepository.findCpfsCadastrados(cpfs))
                : Set.of();
        Map<Long, Set<CategoriaArquivo>> anexos = categoriasEnviadas(registros);
        LocalDate hoje = LocalDate.now();
        return registros.stream()
                .map(p -> PreCadastroResumoDTO.de(p,
                        p.getCpf() != null && cadastrados.contains(p.getCpf()),
                        faltandoSeNaoAprovado(p, anexos.getOrDefault(p.getPreCadastroId(), Set.of())), hoje))
                .toList();
    }

    /** Categorias de anexo de cada pré-cadastro, numa consulta só. */
    private Map<Long, Set<CategoriaArquivo>> categoriasEnviadas(List<PreCadastro> registros) {
        if (registros.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = registros.stream().map(PreCadastro::getPreCadastroId).toList();
        return arquivoRepository.findByEntidadeTipoAndEntidadeIdInAndExcluidoEmIsNull(EntidadeArquivo.PRE_CADASTRO, ids)
                .stream()
                .collect(Collectors.groupingBy(Arquivo::getEntidadeId,
                        Collectors.mapping(Arquivo::getCategoria, Collectors.toSet())));
    }

    /** Aprovado: os arquivos já foram para o atendido, não há o que alertar. */
    private List<String> faltandoSeNaoAprovado(PreCadastro registro, Collection<CategoriaArquivo> enviados) {
        return registro.getStatus() == StatusPreCadastro.CONVERTIDO ? List.of() : anexosFaltando(enviados);
    }

    public PreCadastroResponseDTO abrir(Long preCadastroId) {
        if (usuarioAutenticadoService.temPerfil(NomeNivelPermissao.ASSISTENTE_SOCIAL)) {
            repository.marcarVistoPelaAssistenteSocial(preCadastroId);
        }
        return buscarDetalhe(preCadastroId);
    }

    @Transactional(readOnly = true)
    public PreCadastroResponseDTO buscarDetalhe(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        Long assistidoExistenteId = registro.getStatus().emAvaliacao() && registro.getCpf() != null
                ? assistidoRepository.findByCpf(registro.getCpf()).map(Assistido::getAssistidoId).orElse(null)
                : null;
        boolean jaMatriculadoNoAno = assistidoExistenteId != null
                && matriculaRepository.findByAssistido_AssistidoIdAndAno(assistidoExistenteId, registro.getAnoReferencia()).isPresent();
        List<ArquivoResponseDTO> arquivos = arquivoService.listar(EntidadeArquivo.PRE_CADASTRO, preCadastroId, null);
        List<String> faltando = faltandoSeNaoAprovado(registro,
                arquivos.stream().map(ArquivoResponseDTO::categoria).toList());
        PossivelMesmoAtendido possivel = registro.getStatus().emAvaliacao() && assistidoExistenteId == null
                ? possivelMesmoAtendido(registro)
                : null;
        return PreCadastroResponseDTO.de(registro, assistidoExistenteId, jaMatriculadoNoAno, arquivos, faltando,
                possivel, anoParaMover(registro));
    }

    /**
     * Mesmo nome (sem acento/caixa/espaços extras) e mesma data de nascimento de um atendido,
     * sem bater o CPF: em geral é a ficha antiga sem CPF. Só avisa — irmãos gêmeos existem.
     */
    private PossivelMesmoAtendido possivelMesmoAtendido(PreCadastro registro) {
        if (registro.getDataNascimento() == null || registro.getNomeCompleto() == null) {
            return null;
        }
        String nome = nomeComparavel(registro.getNomeCompleto());
        return assistidoRepository.findByDataNascimento(registro.getDataNascimento()).stream()
                .filter(a -> nomeComparavel(a.getNomeCompleto()).equals(nome))
                .findFirst()
                .map(a -> new PossivelMesmoAtendido(a.getAssistidoId(), a.getNomeCompleto(), a.getCpf() == null))
                .orElse(null);
    }

    private static String nomeComparavel(String nome) {
        return AssistidoSpecification.removerAcentos(nome).replaceAll("\\s+", " ");
    }

    /**
     * Inscrição em avaliação do ano vigente pode ir para o seguinte ("Matrícula do próximo ano")
     * e voltar. Ano vigente: o corrente, ou o seguinte se o corrente já foi encerrado.
     */
    private Integer anoParaMover(PreCadastro registro) {
        if (!registro.getStatus().emAvaliacao()) {
            return null;
        }
        int vigente = matriculaService.anoParaNovoCadastro();
        int ano = registro.getAnoReferencia();
        if (ano == vigente) {
            return vigente + 1;
        }
        return ano == vigente + 1 ? vigente : null;
    }

    /** Move a inscrição para o ano seguinte (ou de volta), sem aprovar: continua em avaliação, agora na lista daquele ano. */
    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public PreCadastroResponseDTO moverParaAno(Long preCadastroId, int ano) {
        PreCadastro registro = buscarEmAvaliacao(preCadastroId);
        Integer permitido = anoParaMover(registro);
        if (permitido == null || permitido != ano) {
            throw new RegraNegocioException(permitido == null
                    ? "A inscrição de " + registro.getAnoReferencia() + " não pode mudar de ano."
                    : "A inscrição de " + registro.getAnoReferencia() + " só pode ir para " + permitido + ".");
        }
        int anterior = registro.getAnoReferencia();
        registro.setAnoReferencia(ano);

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("anoReferencia", anterior), Map.of("anoReferencia", ano),
                ano > anterior ? "Pré-cadastro movido para a matrícula de " + ano
                        : "Pré-cadastro devolvido para a matrícula de " + ano);
        return buscarDetalhe(preCadastroId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UrlArquivoDTO gerarUrlDoArquivo(Long preCadastroId, Long arquivoId) {
        return arquivoService.gerarUrlDePreCadastro(preCadastroId, arquivoId);
    }

    /** Cadastro do atendido já preenchido com o que a família enviou, para a equipe revisar. */
    @Transactional(readOnly = true)
    public AssistidoRequestDTO montarRascunho(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        if (registro.getCpf() == null) {
            throw new RegraNegocioException("Pré-cadastro enviado pelo formulário antigo: reprove e peça um novo envio.");
        }
        // Inscrição do próximo ano: a ficha já nasce com entrada em 1º de janeiro.
        LocalDate hoje = LocalDate.now();
        LocalDate inicioDoAno = LocalDate.of(registro.getAnoReferencia(), 1, 1);
        return paraAssistido.montar(registro, hoje.isBefore(inicioDoAno) ? inicioDoAno : hoje);
    }

    /** Cria o assistido (com os responsáveis) a partir do rascunho revisado pela equipe, numa transação só. */
    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public PreCadastroResponseDTO aprovar(Long preCadastroId, AssistidoRequestDTO dto) {
        PreCadastro registro = buscarEmAvaliacao(preCadastroId);
        String statusAnterior = registro.getStatus().name();
        assistidoRepository.findByCpf(dto.cpf()).ifPresent(existente -> {
            throw new AssistidoJaCadastradoException(existente.getAssistidoId(), existente.getNomeCompleto());
        });

        int ano = registro.getAnoReferencia();
        AssistidoResponseDTO criado = assistidoService.cadastrar(dto, preCadastroId, ano);
        Long matriculaId = matriculaRepository.findByAssistido_AssistidoIdAndAno(criado.assistidoId(), ano)
                .orElseThrow().getMatriculaId();
        anexosDoPreCadastro.transferir(preCadastroId, criado.assistidoId(), matriculaId);
        registro.setStatus(StatusPreCadastro.CONVERTIDO);
        registro.setAssistidoId(criado.assistidoId());
        marcarRevisado(registro);

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", statusAnterior), Map.of("status", "CONVERTIDO", "assistidoId", criado.assistidoId()),
                "Pré-cadastro aprovado: assistido cadastrado");
        return PreCadastroResponseDTO.de(registro, null, false, List.of(), List.of());
    }

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public PreCadastroResponseDTO reprovar(Long preCadastroId, ReprovarPreCadastroRequestDTO dto) {
        PreCadastro registro = buscarEmAvaliacao(preCadastroId);
        String statusAnterior = registro.getStatus().name();
        registro.setStatus(StatusPreCadastro.DESCARTADO);
        registro.setMotivoReprovacao(dto.motivo().trim());
        marcarRevisado(registro);

        // O motivo é texto livre (pode citar saúde, renda...): não vai para a auditoria.
        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", statusAnterior), Map.of("status", "DESCARTADO"), "Pré-cadastro reprovado");
        return buscarDetalhe(preCadastroId);
    }

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public PreCadastroResponseDTO colocarNaListaEspera(Long preCadastroId, ObservacaoPreCadastroRequestDTO dto) {
        PreCadastro registro = buscar(preCadastroId);
        if (registro.getStatus() != StatusPreCadastro.PENDENTE) {
            throw new RegraNegocioException("Só inscrições pendentes podem ir para a lista de espera.");
        }
        registro.setStatus(StatusPreCadastro.LISTA_ESPERA);
        registro.setObservacao(textoOuNulo(dto.observacao()));

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", "PENDENTE"), Map.of("status", "LISTA_ESPERA"), "Pré-cadastro colocado na lista de espera");
        return buscarDetalhe(preCadastroId);
    }

    public void salvarObservacao(Long preCadastroId, ObservacaoPreCadastroRequestDTO dto) {
        PreCadastro registro = buscarEmAvaliacao(preCadastroId);
        registro.setObservacao(textoOuNulo(dto.observacao()));

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                null, null, "Observação do pré-cadastro atualizada");
    }

    private String textoOuNulo(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }

    private void marcarRevisado(PreCadastro registro) {
        registro.setRevisadoPor(usuarioAutenticadoService.getUsuarioAtual());
        registro.setRevisadoEm(OffsetDateTime.now());
    }

    private PreCadastro buscarEmAvaliacao(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        if (!registro.getStatus().emAvaliacao()) {
            throw new RegraNegocioException("Este pré-cadastro já foi revisado.");
        }
        return registro;
    }

    private PreCadastro buscar(Long preCadastroId) {
        return repository.findById(preCadastroId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Pré-cadastro não encontrado: id " + preCadastroId));
    }
}
