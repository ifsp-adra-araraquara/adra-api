package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.precadastro.DescartarPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;
import adra.ifsp.edu.br.api.domain.repository.PreCadastroRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pré-cadastro público (sem login). Grava num rascunho isolado, nunca em
 * assistido — a equipe revisa e cadastra de verdade manualmente.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PreCadastroService {

    /** Mesmo IP não pode enviar outro pré-cadastro antes desse intervalo — mitiga spam/flood simples. */
    private static final Duration JANELA_LIMITE = Duration.ofMinutes(2);

    private final PreCadastroRepository repository;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    private final Map<String, Instant> ultimoEnvioPorIp = new ConcurrentHashMap<>();

    public void registrarPublico(PreCadastroRequestDTO dto, String ip) {
        // Honeypot: campo invisível pro humano: se veio preenchido, é robô — finge sucesso.
        if (dto.site() != null && !dto.site().isBlank()) {
            return;
        }
        if (dto.nomeCompleto() == null || dto.nomeCompleto().isBlank()) {
            throw new RegraNegocioException("Informe o nome completo.");
        }
        if (dto.responsavelNome() == null || dto.responsavelNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do responsável.");
        }

        Instant agora = Instant.now();
        Instant ultimo = ip != null ? ultimoEnvioPorIp.get(ip) : null;
        if (ultimo != null && Duration.between(ultimo, agora).compareTo(JANELA_LIMITE) < 0) {
            throw new RegraNegocioException("Aguarde um pouco antes de enviar outro pré-cadastro.");
        }
        if (ip != null) {
            ultimoEnvioPorIp.put(ip, agora);
        }

        PreCadastro registro = PreCadastro.builder()
                .nomeCompleto(dto.nomeCompleto().trim())
                .dataNascimento(dto.dataNascimento())
                .responsavelNome(dto.responsavelNome().trim())
                .responsavelTelefone(dto.responsavelTelefone())
                .endereco(dto.endereco())
                .observacao(dto.observacao())
                .ipOrigem(ip)
                .build();
        repository.save(registro);

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", registro.getPreCadastroId(),
                AcaoSistema.CRIAR, null, Map.of("nomeCompleto", registro.getNomeCompleto()),
                "Pré-cadastro recebido pelo link público");
    }

    @Transactional(readOnly = true)
    public List<PreCadastroResponseDTO> listarPendentes() {
        return repository.findByStatusOrderByCriadoEmDesc(StatusPreCadastro.PENDENTE).stream()
                .map(PreCadastroResponseDTO::de)
                .toList();
    }

    public PreCadastroResponseDTO descartar(Long preCadastroId, DescartarPreCadastroRequestDTO dto) {
        PreCadastro registro = buscar(preCadastroId);
        if (registro.getStatus() != StatusPreCadastro.PENDENTE) {
            throw new RegraNegocioException("Este pré-cadastro já foi revisado.");
        }
        registro.setStatus(StatusPreCadastro.DESCARTADO);
        registro.setRevisadoPor(usuarioAutenticadoService.getUsuarioAtual());
        registro.setRevisadoEm(OffsetDateTime.now());

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", "PENDENTE"), Map.of("status", "DESCARTADO", "motivo", dto != null ? dto.motivo() : null),
                "Pré-cadastro descartado");
        return PreCadastroResponseDTO.de(registro);
    }

    /** Marca como convertido depois que a equipe cadastra o assistido de verdade (fora deste fluxo). */
    public PreCadastroResponseDTO marcarConvertido(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        if (registro.getStatus() != StatusPreCadastro.PENDENTE) {
            throw new RegraNegocioException("Este pré-cadastro já foi revisado.");
        }
        registro.setStatus(StatusPreCadastro.CONVERTIDO);
        registro.setRevisadoPor(usuarioAutenticadoService.getUsuarioAtual());
        registro.setRevisadoEm(OffsetDateTime.now());

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", "PENDENTE"), Map.of("status", "CONVERTIDO"), "Pré-cadastro convertido em assistido");
        return PreCadastroResponseDTO.de(registro);
    }

    private PreCadastro buscar(Long preCadastroId) {
        return repository.findById(preCadastroId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Pré-cadastro não encontrado: id " + preCadastroId));
    }
}
