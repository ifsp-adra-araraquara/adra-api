package adra.ifsp.edu.br.api.domain.service;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import adra.ifsp.edu.br.api.domain.dto.arquivo.ArquivoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.arquivo.UrlArquivoDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.model.Arquivo;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.ArquivoInvalidoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ArquivoService {

    private static final Logger log = LoggerFactory.getLogger(ArquivoService.class);
    private static final int MAXIMO_FOTOS_POR_LOTE = 100;

    public record AnexoNovo(CategoriaArquivo categoria, MultipartFile arquivo) {
    }

    public record ArquivoEnviado(CategoriaArquivo categoria, ProvedorArmazenamento provedor, String chave,
                                 String nomeOriginal, String mimeType, long tamanhoBytes, String hashSha256) {
    }

    private final ArquivoRepository arquivoRepository;
    private final AssistidoRepository assistidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ResponsavelRepository responsavelRepository;
    private final SemanarioRepository semanarioRepository;
    private final PreCadastroRepository preCadastroRepository;
    private final AcessoTurmaService acessoTurma;
    private final ProcessadorArquivo processador;
    private final List<ArmazenamentoArquivos> armazenamentos;
    private final RemocaoNoArmazenamento remocao;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transacao;

    /** Onde os arquivos NOVOS vão. Os antigos são lidos pelo provedor gravado em cada linha. */
    @Value("${adra.arquivos.provedor}")
    private ProvedorArmazenamento provedorAtivo;

    @Value("${adra.arquivos.url-validade:5m}")
    private Duration validadeUrl;

    @CacheEvict(cacheNames = {CacheConfig.FOTOS_PERFIL, CacheConfig.URLS_ARQUIVO}, allEntries = true)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ArquivoResponseDTO salvar(EntidadeArquivo entidadeTipo, Long entidadeId,
                                     CategoriaArquivo categoria, MultipartFile arquivo) {
        validarDono(entidadeTipo, entidadeId);
        return enviarEGravar(entidadeTipo, List.of(new AnexoNovo(categoria, arquivo)),
                enviados -> registrar(entidadeTipo, entidadeId, enviados.getFirst()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public <T> T enviarEGravar(EntidadeArquivo entidadeTipo, List<AnexoNovo> anexos,
                               Function<List<ArquivoEnviado>, T> gravacao) {
        List<ArquivoEnviado> enviados = new ArrayList<>();
        try {
            for (AnexoNovo anexo : anexos) {
                enviados.add(enviarAoStorage(entidadeTipo, anexo));
            }
            return transacao.execute(status -> gravacao.apply(enviados));
        } catch (RuntimeException e) {
            enviados.forEach(this::apagarDoStorage);
            throw e;
        }
    }

    @CacheEvict(cacheNames = {CacheConfig.FOTOS_PERFIL, CacheConfig.URLS_ARQUIVO}, allEntries = true)
    public ArquivoResponseDTO registrar(EntidadeArquivo entidadeTipo, Long entidadeId, ArquivoEnviado enviado) {
        return gravarMetadados(entidadeTipo, entidadeId, enviado, usuarioAutenticadoService.getUsuarioAtual());
    }

    /** Link público do pré-cadastro: sem login, então sem "enviado por". */
    @CacheEvict(cacheNames = {CacheConfig.FOTOS_PERFIL, CacheConfig.URLS_ARQUIVO}, allEntries = true)
    public ArquivoResponseDTO registrarDoLinkPublico(Long preCadastroId, ArquivoEnviado enviado) {
        return gravarMetadados(EntidadeArquivo.PRE_CADASTRO, preCadastroId, enviado, null);
    }

    private ArquivoEnviado enviarAoStorage(EntidadeArquivo entidadeTipo, AnexoNovo anexo) {
        MultipartFile arquivo = anexo.arquivo();
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Envie um arquivo.");
        }

        ProcessadorArquivo.ArquivoProcessado processado = processador.processar(lerBytes(arquivo), anexo.categoria());
        String chave = montarChave(entidadeTipo, anexo.categoria(), processado.extensao());
        ArmazenamentoArquivos storage = armazenamento(provedorAtivo);
        storage.salvar(chave, processado.conteudo(), processado.mimeType());

        return new ArquivoEnviado(anexo.categoria(), storage.provedor(), chave, nomeSeguro(arquivo.getOriginalFilename()),
                processado.mimeType(), processado.conteudo().length, sha256(processado.conteudo()));
    }

    private ArquivoResponseDTO gravarMetadados(EntidadeArquivo entidadeTipo, Long entidadeId, ArquivoEnviado enviado,
                                               Usuario enviadoPor) {
        CategoriaArquivo categoria = enviado.categoria();

        var identico = arquivoRepository
                .findFirstByEntidadeTipoAndEntidadeIdAndCategoriaAndHashSha256AndExcluidoEmIsNull(
                        entidadeTipo, entidadeId, categoria, enviado.hashSha256());
        if (identico.isPresent()) {
            aposCommit(() -> apagarDoStorage(enviado));
            return ArquivoResponseDTO.de(identico.get());
        }

        if (categoria.isUnicoPorDono()) {
            substituirAnteriores(entidadeTipo, entidadeId, categoria);
        }

        Arquivo salvo = arquivoRepository.save(Arquivo.builder()
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .categoria(categoria)
                .provedor(enviado.provedor())
                .chave(enviado.chave())
                .nomeOriginal(enviado.nomeOriginal())
                .mimeType(enviado.mimeType())
                .tamanhoBytes(enviado.tamanhoBytes())
                .hashSha256(enviado.hashSha256())
                .enviadoPor(enviadoPor)
                .build());

        auditoriaService.registrar(ModuloSistema.DOCUMENTOS, "arquivo", salvo.getArquivoId(),
                AcaoSistema.CRIAR, null,
                Map.of("entidadeTipo", entidadeTipo.name(), "entidadeId", entidadeId,
                        "categoria", categoria.name(), "tamanhoBytes", salvo.getTamanhoBytes()),
                null);

        return ArquivoResponseDTO.de(salvo);
    }

    @Cacheable(cacheNames = CacheConfig.FOTOS_PERFIL,
            condition = "#categoria == T(adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo).FOTO_PERFIL")
    @Transactional(readOnly = true)
    public List<ArquivoResponseDTO> listar(EntidadeArquivo entidadeTipo, Long entidadeId, CategoriaArquivo categoria) {
        List<Arquivo> arquivos = categoria == null
                ? arquivoRepository.findByEntidadeTipoAndEntidadeIdAndExcluidoEmIsNullOrderByCriadoEmDesc(
                entidadeTipo, entidadeId)
                : arquivoRepository.findByEntidadeTipoAndEntidadeIdAndCategoriaAndExcluidoEmIsNullOrderByCriadoEmDesc(
                entidadeTipo, entidadeId, categoria);
        if (entidadeTipo == EntidadeArquivo.SEMANARIO) {
            arquivos = somenteVisiveisAoUsuario(arquivos);
        }
        return arquivos.stream().map(ArquivoResponseDTO::de).toList();
    }

    /**
     * Material do semanário: o Oficineiro só enxerga o que ELE enviou;
     * Coordenação/Sociopedagógico/Admin enxergam tudo (com o nome de quem enviou).
     */
    private List<Arquivo> somenteVisiveisAoUsuario(List<Arquivo> arquivos) {
        if (!acessoTurma.usuarioAtualEhOficineiro()) return arquivos;
        Long eu = acessoTurma.usuarioIdAtual();
        return arquivos.stream()
                .filter(a -> a.getEnviadoPor() != null && a.getEnviadoPor().getUsuarioId().equals(eu))
                .toList();
    }

    @Cacheable(CacheConfig.URLS_ARQUIVO)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UrlArquivoDTO gerarUrl(Long arquivoId) {
        Arquivo arquivo = buscarAtivo(arquivoId);
        // Arquivo do pré-cadastro só pela tela de pré-cadastros (Coordenação e Assistente Social).
        if (arquivo.getEntidadeTipo() == EntidadeArquivo.PRE_CADASTRO) {
            throw new AcessoNegadoException("Arquivo de pré-cadastro: abra pela tela de pré-cadastros.");
        }
        return urlTemporaria(arquivo);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UrlArquivoDTO gerarUrlDePreCadastro(Long preCadastroId, Long arquivoId) {
        Arquivo arquivo = buscarAtivo(arquivoId);
        if (arquivo.getEntidadeTipo() != EntidadeArquivo.PRE_CADASTRO || !arquivo.getEntidadeId().equals(preCadastroId)) {
            throw new EntidadeNaoEncontradaException("Arquivo nao encontrado neste pré-cadastro: id " + arquivoId);
        }
        return urlTemporaria(arquivo);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<Long, UrlArquivoDTO> urlsDasFotosDePerfil(EntidadeArquivo entidadeTipo, List<Long> entidadeIds) {
        if (entidadeIds.size() > MAXIMO_FOTOS_POR_LOTE) {
            throw new ArquivoInvalidoException("Peça no máximo " + MAXIMO_FOTOS_POR_LOTE + " fotos por vez.");
        }

        List<Arquivo> fotos = arquivoRepository.findByEntidadeTipoAndEntidadeIdInAndCategoriaAndExcluidoEmIsNull(
                entidadeTipo, entidadeIds, CategoriaArquivo.FOTO_PERFIL);
        Instant expiraEm = Instant.now().plus(validadeUrl);

        Map<Long, UrlArquivoDTO> urls = new HashMap<>();
        fotos.stream().collect(Collectors.groupingBy(Arquivo::getProvedor)).forEach((provedor, fotosDoProvedor) -> {
            Map<String, URI> assinadas = armazenamento(provedor).urlsTemporarias(
                    fotosDoProvedor.stream().map(Arquivo::getChave).toList(), validadeUrl);
            for (Arquivo foto : fotosDoProvedor) {
                URI assinada = assinadas.get(foto.getChave());
                if (assinada != null) {
                    urls.put(foto.getEntidadeId(), new UrlArquivoDTO(assinada.toString(), expiraEm));
                }
            }
        });
        return urls;
    }

    /**
     * Passa os arquivos de um dono para outro (pré-cadastro aprovado → assistido/responsável).
     * O binário fica onde está no storage; só a linha muda de dono.
     */
    @CacheEvict(cacheNames = {CacheConfig.FOTOS_PERFIL, CacheConfig.URLS_ARQUIVO}, allEntries = true)
    public void transferir(EntidadeArquivo deTipo, Long deId, CategoriaArquivo deCategoria,
                           EntidadeArquivo paraTipo, Long paraId, CategoriaArquivo paraCategoria) {
        transferir(deTipo, deId, deCategoria, paraTipo, paraId, paraCategoria, null);
    }

    /**
     * Passa os arquivos ativos para o novo dono, marcando a matrícula do ano a que
     * pertencem ({@code matriculaId}, null para os que não são de matrícula).
     * Categoria única por dono (foto de perfil): a nova substitui a atual, que sai
     * do Storage depois do commit — não fica duplicada nem órfã.
     */
    @CacheEvict(cacheNames = {CacheConfig.FOTOS_PERFIL, CacheConfig.URLS_ARQUIVO}, allEntries = true)
    public void transferir(EntidadeArquivo deTipo, Long deId, CategoriaArquivo deCategoria,
                           EntidadeArquivo paraTipo, Long paraId, CategoriaArquivo paraCategoria, Long matriculaId) {
        if (paraCategoria.isUnicoPorDono()
                && arquivoRepository.existsByEntidadeTipoAndEntidadeIdAndCategoriaAndExcluidoEmIsNull(deTipo, deId, deCategoria)) {
            substituirAnteriores(paraTipo, paraId, paraCategoria);
        }
        arquivoRepository.transferirDono(deTipo, deId, deCategoria, paraTipo, paraId, paraCategoria, matriculaId);
    }

    private UrlArquivoDTO urlTemporaria(Arquivo arquivo) {
        var url = armazenamento(arquivo.getProvedor()).urlTemporaria(arquivo.getChave(), validadeUrl);
        return new UrlArquivoDTO(url.toString(), Instant.now().plus(validadeUrl));
    }

    /** Some da listagem na hora; o binário só sai do storage depois do commit. */
    @CacheEvict(cacheNames = {CacheConfig.FOTOS_PERFIL, CacheConfig.URLS_ARQUIVO}, allEntries = true)
    public void remover(Long arquivoId) {
        Arquivo arquivo = buscarAtivo(arquivoId);
        arquivo.marcarComoExcluido();
        String chave = arquivo.getChave();
        ProvedorArmazenamento provedor = arquivo.getProvedor();
        aposCommit(() -> removerDoStorageSemFalhar(provedor, chave));

        auditoriaService.registrar(ModuloSistema.DOCUMENTOS, "arquivo", arquivoId, AcaoSistema.EXCLUIR,
                Map.of("categoria", arquivo.getCategoria().name()), null, null);
    }

    // ------------------------------------------------------------

    private void substituirAnteriores(EntidadeArquivo entidadeTipo, Long entidadeId, CategoriaArquivo categoria) {
        List<Arquivo> anteriores = arquivoRepository
                .findByEntidadeTipoAndEntidadeIdAndCategoriaAndExcluidoEmIsNullOrderByCriadoEmDesc(
                        entidadeTipo, entidadeId, categoria);
        if (anteriores.isEmpty()) return;

        anteriores.forEach(Arquivo::marcarComoExcluido);
        // flush AGORA: o Hibernate executa INSERTs antes de UPDATEs, e o índice
        // único da foto ativa reclamaria de duas fotos "vivas" ao mesmo tempo.
        arquivoRepository.saveAllAndFlush(anteriores);

        for (Arquivo antigo : anteriores) {
            String chave = antigo.getChave();
            ProvedorArmazenamento provedor = antigo.getProvedor();
            aposCommit(() -> removerDoStorageSemFalhar(provedor, chave));
        }
    }

    private void validarDono(EntidadeArquivo entidadeTipo, Long entidadeId) {
        if (entidadeTipo == null || entidadeId == null) {
            throw new ArquivoInvalidoException("Informe a quem o arquivo pertence.");
        }
        if(entidadeTipo==EntidadeArquivo.ASSISTIDO){
            if (!assistidoRepository.existsById(entidadeId)) {
                throw new EntidadeNaoEncontradaException(entidadeTipo + " nao encontrado: id " + entidadeId);
            }
        } else if(entidadeTipo==EntidadeArquivo.USUARIO){
            if (!usuarioRepository.existsById(entidadeId)) {
                throw new EntidadeNaoEncontradaException(entidadeTipo + " nao encontrado: id " + entidadeId);
            }
        } else if(entidadeTipo==EntidadeArquivo.RESPONSAVEL){
            if (!responsavelRepository.existsById(entidadeId)) {
                throw new EntidadeNaoEncontradaException(entidadeTipo + " nao encontrado: id " + entidadeId);
            }
        } else if(entidadeTipo==EntidadeArquivo.SEMANARIO){
            if (!semanarioRepository.existsById(entidadeId)) {
                throw new EntidadeNaoEncontradaException(entidadeTipo + " nao encontrado: id " + entidadeId);
            }
        } else if(entidadeTipo==EntidadeArquivo.PRE_CADASTRO){
            if (!preCadastroRepository.existsById(entidadeId)) {
                throw new EntidadeNaoEncontradaException(entidadeTipo + " nao encontrado: id " + entidadeId);
            }
        } else {
            throw new ArquivoInvalidoException("Arquivos so podem ser enviados para atendidos, responsaveis, usuarios e semanarios.");
        }
    }

    private Arquivo buscarAtivo(Long arquivoId) {
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Arquivo nao encontrado: id " + arquivoId));
    }

    private ArmazenamentoArquivos armazenamento(ProvedorArmazenamento provedor) {
        return armazenamentos.stream()
                .filter(a -> a.provedor() == provedor)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhum adaptador de storage para " + provedor));
    }

    /** Sem nome de pessoa no caminho (LGPD): assistido/foto_perfil/<uuid>.webp */
    private static String montarChave(EntidadeArquivo tipo, CategoriaArquivo categoria, String extensao) {
        return tipo.name().toLowerCase() + "/" + categoria.name().toLowerCase()
                + "/" + UUID.randomUUID() + "." + extensao;
    }

    private static String nomeSeguro(String original) {
        String nome = StringUtils.getFilename(original);
        if (nome == null || nome.isBlank()) return null;
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    private static byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new ArquivoInvalidoException("Nao foi possivel ler o arquivo enviado.");
        }
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void apagarDoStorage(ArquivoEnviado enviado) {
        removerDoStorageSemFalhar(enviado.provedor(), enviado.chave());
    }

    /** Em segundo plano, sem derrubar a requisição; se o Storage falhar, a chave vai para a fila de remoção. */
    private void removerDoStorageSemFalhar(ProvedorArmazenamento provedor, String chave) {
        Thread.startVirtualThread(() -> remocao.removerOuEnfileirar(new RemocaoNoArmazenamento.Chave(provedor, chave)));
    }

    private static void aposCommit(Runnable acao) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                acao.run();
            }
        });
    }
}