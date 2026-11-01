package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Mae;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.ResponsavelLegal;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResumoDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.ReprovarPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.mapper.PreCadastroParaAssistido;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.PreCadastroRepository;
import adra.ifsp.edu.br.api.exception.AssistidoJaCadastradoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    private static final int LIMITE_DIARIO_POR_IP = 10;

    private final PreCadastroRepository repository;
    private final AssistidoRepository assistidoRepository;
    private final AssistidoService assistidoService;
    private final PreCadastroParaAssistido paraAssistido;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    private final Map<String, EnviosDoIp> enviosPorIp = new ConcurrentHashMap<>();

    private record EnviosDoIp(Instant ultimo, LocalDate dia, int totalNoDia) {
    }

    /** Devolve o id (protocolo para a família); null quando o honeypot pegou um robô. */
    public Long registrarPublico(PreCadastroRequestDTO dto, String ip) {
        // Honeypot: campo invisível pro humano: se veio preenchido, é robô — finge sucesso.
        if (dto.site() != null && !dto.site().isBlank()) {
            return null;
        }
        validarCondicionais(dto.dados());
        registrarEnvio(ip, Instant.now());

        PreCadastro registro = PreCadastro.builder()
                .nomeCompleto(dto.nomeCompleto().trim())
                .dataNascimento(dto.dataNascimento())
                .cpf(dto.cpf())
                .sexo(dto.sexo())
                .email(StringUtils.hasText(dto.email()) ? dto.email().trim() : null)
                .responsavelNome(nomeDoResponsavel(dto.dados()))
                .responsavelTelefone(telefoneDoResponsavel(dto.dados()))
                .dados(dto.dados())
                .ipOrigem(ip)
                .build();
        repository.save(registro);

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", registro.getPreCadastroId(),
                AcaoSistema.CRIAR, null, Map.of("nomeCompleto", registro.getNomeCompleto()),
                "Pré-cadastro recebido pelo link público");
        return registro.getPreCadastroId();
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
            throw new RegraNegocioException("Informe qual é o problema de saúde da criança.");
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
            throw new RegraNegocioException("Informe quem vem buscar a criança.");
        }
    }

    /** 1 envio a cada 2 minutos e no máximo 10 por dia, por IP (em memória: a API roda numa instância só). */
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
    public List<PreCadastroResumoDTO> listar(StatusPreCadastro status) {
        List<PreCadastro> registros = repository.findByStatusOrderByCriadoEmDesc(status);
        Set<String> cpfs = registros.stream()
                .map(PreCadastro::getCpf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> cadastrados = cpfs.isEmpty() ? Set.of() : new HashSet<>(assistidoRepository.findCpfsCadastrados(cpfs));
        LocalDate hoje = LocalDate.now();
        return registros.stream()
                .map(p -> PreCadastroResumoDTO.de(p,
                        p.getStatus() == StatusPreCadastro.PENDENTE && cadastrados.contains(p.getCpf()), hoje))
                .toList();
    }

    @Transactional(readOnly = true)
    public PreCadastroResponseDTO buscarDetalhe(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        Long assistidoExistenteId = registro.getStatus() == StatusPreCadastro.PENDENTE && registro.getCpf() != null
                ? assistidoRepository.findByCpf(registro.getCpf()).map(Assistido::getAssistidoId).orElse(null)
                : null;
        return PreCadastroResponseDTO.de(registro, assistidoExistenteId);
    }

    /** Cadastro do atendido já preenchido com o que a família enviou, para a equipe revisar. */
    @Transactional(readOnly = true)
    public AssistidoRequestDTO montarRascunho(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        if (registro.getCpf() == null) {
            throw new RegraNegocioException("Pré-cadastro enviado pelo formulário antigo: reprove e peça um novo envio.");
        }
        return paraAssistido.montar(registro, LocalDate.now());
    }

    /** Cria o assistido (com os responsáveis) a partir do rascunho revisado pela equipe, numa transação só. */
    public PreCadastroResponseDTO aprovar(Long preCadastroId, AssistidoRequestDTO dto) {
        PreCadastro registro = buscarPendente(preCadastroId);
        assistidoRepository.findByCpf(dto.cpf()).ifPresent(existente -> {
            throw new AssistidoJaCadastradoException(existente.getAssistidoId(), existente.getNomeCompleto());
        });

        AssistidoResponseDTO criado = assistidoService.cadastrar(dto);
        registro.setStatus(StatusPreCadastro.CONVERTIDO);
        registro.setAssistidoId(criado.assistidoId());
        marcarRevisado(registro);

        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", "PENDENTE"), Map.of("status", "CONVERTIDO", "assistidoId", criado.assistidoId()),
                "Pré-cadastro aprovado: assistido cadastrado");
        return PreCadastroResponseDTO.de(registro, null);
    }

    public PreCadastroResponseDTO reprovar(Long preCadastroId, ReprovarPreCadastroRequestDTO dto) {
        PreCadastro registro = buscarPendente(preCadastroId);
        registro.setStatus(StatusPreCadastro.DESCARTADO);
        registro.setMotivoReprovacao(dto.motivo().trim());
        marcarRevisado(registro);

        // O motivo é texto livre (pode citar saúde, renda...): não vai para a auditoria.
        auditoriaService.registrar(ModuloSistema.PRE_CADASTRO, "pre_cadastro", preCadastroId, AcaoSistema.EDITAR,
                Map.of("status", "PENDENTE"), Map.of("status", "DESCARTADO"), "Pré-cadastro reprovado");
        return PreCadastroResponseDTO.de(registro, null);
    }

    private void marcarRevisado(PreCadastro registro) {
        registro.setRevisadoPor(usuarioAutenticadoService.getUsuarioAtual());
        registro.setRevisadoEm(OffsetDateTime.now());
    }

    private PreCadastro buscarPendente(Long preCadastroId) {
        PreCadastro registro = buscar(preCadastroId);
        if (registro.getStatus() != StatusPreCadastro.PENDENTE) {
            throw new RegraNegocioException("Este pré-cadastro já foi revisado.");
        }
        return registro;
    }

    private PreCadastro buscar(Long preCadastroId) {
        return repository.findById(preCadastroId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Pré-cadastro não encontrado: id " + preCadastroId));
    }
}
