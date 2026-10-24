package adra.ifsp.edu.br.api.domain.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import adra.ifsp.edu.br.api.domain.dto.arquivo.AulaArquivoDTO;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import adra.ifsp.edu.br.api.exception.ArquivoInvalidoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ArquivoService {

    private static final Logger log = LoggerFactory.getLogger(ArquivoService.class);

    private final ArquivoRepository arquivoRepository;
    private final AssistidoRepository assistidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ResponsavelRepository responsavelRepository;
    private final AulaRepository aulaRepository;
    private final TurmaRepository turmaRepository;
    private final AcessoTurmaService acessoTurma;
    private final ProcessadorArquivo processador;
    private final List<ArmazenamentoArquivos> armazenamentos;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final AuditoriaService auditoriaService;

    /** Onde os arquivos NOVOS vão. Os antigos são lidos pelo provedor gravado em cada linha. */
    @Value("${adra.arquivos.provedor}")
    private ProvedorArmazenamento provedorAtivo;

    @Value("${adra.arquivos.url-validade:5m}")
    private Duration validadeUrl;

    /**
     * Valida, comprime, envia ao storage e grava os metadados. Roda dentro da
     * transação de quem chamou (ex.: cadastro do assistido): se ela falhar
     * depois do upload, o binário é apagado do storage.
     */
    public ArquivoResponseDTO salvar(EntidadeArquivo entidadeTipo, Long entidadeId,
                                     CategoriaArquivo categoria, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Envie um arquivo.");
        }
        validarDono(entidadeTipo, entidadeId);

        ProcessadorArquivo.ArquivoProcessado processado = processador.processar(lerBytes(arquivo), categoria);
        String hash = sha256(processado.conteudo());

        var identico = arquivoRepository
                .findFirstByEntidadeTipoAndEntidadeIdAndCategoriaAndHashSha256AndExcluidoEmIsNull(
                        entidadeTipo, entidadeId, categoria, hash);
        if (identico.isPresent()) {
            return ArquivoResponseDTO.de(identico.get());
        }

        if (categoria.isUnicoPorDono()) {
            substituirAnteriores(entidadeTipo, entidadeId, categoria);
        }

        String chave = montarChave(entidadeTipo, entidadeId, categoria, processado.extensao());
        ArmazenamentoArquivos storage = armazenamento(provedorAtivo);
        storage.salvar(chave, processado.conteudo(), processado.mimeType());
        aposRollback(() -> removerDoStorageSemFalhar(storage, chave));

        Arquivo salvo = arquivoRepository.save(Arquivo.builder()
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .categoria(categoria)
                .provedor(storage.provedor())
                .chave(chave)
                .nomeOriginal(nomeSeguro(arquivo.getOriginalFilename()))
                .mimeType(processado.mimeType())
                .tamanhoBytes((long) processado.conteudo().length)
                .hashSha256(hash)
                .enviadoPor(usuarioAutenticadoService.getUsuarioAtual())
                .build());

        auditoriaService.registrar(ModuloSistema.DOCUMENTOS, "arquivo", salvo.getArquivoId(),
                AcaoSistema.CRIAR, null,
                Map.of("entidadeTipo", entidadeTipo.name(), "entidadeId", entidadeId,
                        "categoria", categoria.name(), "tamanhoBytes", salvo.getTamanhoBytes()),
                null);

        return ArquivoResponseDTO.de(salvo);
    }

    @Transactional(readOnly = true)
    public List<ArquivoResponseDTO> listar(EntidadeArquivo entidadeTipo, Long entidadeId, CategoriaArquivo categoria) {
        List<Arquivo> arquivos = categoria == null
                ? arquivoRepository.findByEntidadeTipoAndEntidadeIdAndExcluidoEmIsNullOrderByCriadoEmDesc(
                entidadeTipo, entidadeId)
                : arquivoRepository.findByEntidadeTipoAndEntidadeIdAndCategoriaAndExcluidoEmIsNullOrderByCriadoEmDesc(
                entidadeTipo, entidadeId, categoria);
        if (entidadeTipo == EntidadeArquivo.AULA) {
            arquivos = somenteVisiveisAoUsuario(arquivos);
        }
        return arquivos.stream().map(ArquivoResponseDTO::de).toList();
    }

    /**
     * Material de aula: o Oficineiro só enxerga o que ELE enviou;
     * Coordenação/Sociopedagógico/Admin enxergam tudo (com o nome de quem enviou).
     */
    private List<Arquivo> somenteVisiveisAoUsuario(List<Arquivo> arquivos) {
        if (!acessoTurma.usuarioAtualEhOficineiro()) return arquivos;
        Long eu = acessoTurma.usuarioIdAtual();
        return arquivos.stream()
                .filter(a -> a.getEnviadoPor() != null && a.getEnviadoPor().getUsuarioId().equals(eu))
                .toList();
    }

    /**
     * Aulas da turma que têm material, cada uma com seus arquivos (mais recentes primeiro).
     * Aula sem material não entra. Uma consulta de arquivos por aula — ok pro volume atual;
     * se pesar, trocar por uma busca com EntidadeIdIn e agrupar por entidadeId.
     */
    @Transactional(readOnly = true)
    public List<AulaArquivoDTO> listarArquivosPorAula(Long turmaId) {
        List<AulaArquivoDTO> aulasArquivos = new ArrayList<>();

        Turma turma = turmaRepository.findById(turmaId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma nao encontrada: id " + turmaId));

        List<Aula> aulas = aulaRepository.findByTurmaOrderByDataAulaDescHorarioInicioDesc(turma);

        for (Aula aula : aulas) {
            List<Arquivo> arquivos = arquivoRepository
                    .findByEntidadeTipoAndEntidadeIdAndCategoriaAndExcluidoEmIsNullOrderByCriadoEmDesc(
                            EntidadeArquivo.AULA, aula.getAulaId(), CategoriaArquivo.MATERIAL_AULA);
            arquivos = somenteVisiveisAoUsuario(arquivos);

            if (!arquivos.isEmpty()) {
                aulasArquivos.add(AulaArquivoDTO.de(aula, arquivos));
            }
        }
        return aulasArquivos;
    }




    @Transactional(readOnly = true)
    public UrlArquivoDTO gerarUrl(Long arquivoId) {
        Arquivo arquivo = buscarAtivo(arquivoId);
        var url = armazenamento(arquivo.getProvedor()).urlTemporaria(arquivo.getChave(), validadeUrl);
        return new UrlArquivoDTO(url.toString(), Instant.now().plus(validadeUrl));
    }

    /** Some da listagem na hora; o binário só sai do storage depois do commit. */
    public void remover(Long arquivoId) {
        Arquivo arquivo = buscarAtivo(arquivoId);
        arquivo.marcarComoExcluido();
        ArmazenamentoArquivos storage = armazenamento(arquivo.getProvedor());
        String chave = arquivo.getChave();
        aposCommit(() -> removerDoStorageSemFalhar(storage, chave));

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
            ArmazenamentoArquivos storage = armazenamento(antigo.getProvedor());
            String chave = antigo.getChave();
            aposCommit(() -> removerDoStorageSemFalhar(storage, chave));
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
        } else if(entidadeTipo==EntidadeArquivo.AULA){
            if (!aulaRepository.existsById(entidadeId)) {
                throw new EntidadeNaoEncontradaException(entidadeTipo + " nao encontrado: id " + entidadeId);
            }
        } else {
            throw new ArquivoInvalidoException("Arquivos so podem ser enviados para assistidos, responsaveis, usuarios e aulas.");
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

    /** Sem nome de pessoa no caminho (LGPD): assistido/123/foto_perfil/<uuid>.webp */
    private static String montarChave(EntidadeArquivo tipo, Long id, CategoriaArquivo categoria, String extensao) {
        return tipo.name().toLowerCase() + "/" + id + "/" + categoria.name().toLowerCase()
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

    private static void removerDoStorageSemFalhar(ArmazenamentoArquivos storage, String chave) {
        try {
            storage.remover(chave);
        } catch (RuntimeException e) {
            // Fica órfão no bucket, mas não derruba a requisição. Dá pra varrer depois.
            log.warn("Nao foi possivel remover {} do storage {}", chave, storage.provedor(), e);
        }
    }

    private static void aposCommit(Runnable acao) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                acao.run();
            }
        });
    }

    private static void aposRollback(Runnable acao) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) acao.run();
            }
        });
    }
}