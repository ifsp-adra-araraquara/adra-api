package adra.ifsp.edu.br.api.domain.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.desligamento.SolicitacaoDesligamentoDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.PedidoNotificacao;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusSolicitacaoDesligamento;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Notificacao.Envolvido;
import adra.ifsp.edu.br.api.domain.model.SolicitacaoDesligamento;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.SolicitacaoDesligamentoRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;

/**
 * Pedido de desligamento (pedido da ADRA de 13/11/2026): a Assistente Social pede,
 * o atendido continua ativo, e só o Coordenador aprova (inativa) ou recusa.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SolicitacaoDesligamentoService {

    /** Decisões recentes continuam na lista, para quem pediu ver a resposta. */
    static final int DIAS_DECISOES_RECENTES = 30;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SolicitacaoDesligamentoRepository repository;
    private final AssistidoRepository assistidoRepository;
    private final AssistidoService assistidoService;
    private final NotificacaoService notificacaoService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final AuditoriaService auditoriaService;

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public SolicitacaoDesligamentoDTO solicitar(Long assistidoId, java.time.LocalDate dataSaida, String motivo) {
        Assistido assistido = assistidoRepository.findById(assistidoId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Atendido não encontrado: id " + assistidoId));
        if (assistido.getStatus() != StatusGeral.ATIVO) {
            throw new RegraNegocioException(assistido.getNomeCompleto() + " já está inativo.");
        }
        if (repository.existsByAssistido_AssistidoIdAndStatus(assistidoId, StatusSolicitacaoDesligamento.PENDENTE)) {
            throw new RegraNegocioException("Já existe um pedido de desligamento em aberto para " + assistido.getNomeCompleto() + ".");
        }
        Usuario solicitante = usuarioAutenticadoService.getUsuarioAtual();
        SolicitacaoDesligamento solicitacao = repository.save(
                SolicitacaoDesligamento.nova(assistido, solicitante, dataSaida, motivo.trim()));

        // O motivo é texto livre (pode citar família, saúde...): não vai na notificação nem no e-mail.
        notificacaoService.notificar(new PedidoNotificacao(
                TipoAviso.DESLIGAMENTO_SOLICITADO,
                "solicitacao:" + solicitacao.getSolicitacaoId(),
                "Desligamento solicitado: " + assistido.getNomeCompleto(),
                "Pedido de " + solicitante.getNomeCompleto() + " · saída em " + dataSaida.format(DATA)
                        + ". Abra o sistema para aprovar ou recusar.",
                "/assistidos?desligamentos=1",
                Set.of(Envolvido.assistido(assistidoId), Envolvido.usuario(solicitante.getUsuarioId())),
                notificacaoService.comPerfil(NomeNivelPermissao.COORDENADOR),
                PedidoNotificacao.TODOS_OS_CANAIS));

        auditoriaService.registrar(ModuloSistema.ASSISTIDOS, "solicitacao_desligamento", solicitacao.getSolicitacaoId(),
                AcaoSistema.CRIAR, null, Map.of("assistidoId", assistidoId, "dataSaida", dataSaida.toString()),
                "Desligamento solicitado");
        return SolicitacaoDesligamentoDTO.de(solicitacao);
    }

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public SolicitacaoDesligamentoDTO cancelar(Long solicitacaoId) {
        SolicitacaoDesligamento solicitacao = pendente(solicitacaoId);
        solicitacao.decidir(StatusSolicitacaoDesligamento.CANCELADA, "Pedido retirado.", usuarioAutenticadoService.getUsuarioAtual());
        registrar(solicitacao, "Pedido de desligamento cancelado");
        return SolicitacaoDesligamentoDTO.de(solicitacao);
    }

    /** Aprovar = inativar o atendido com a data e o motivo do pedido. */
    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public SolicitacaoDesligamentoDTO aprovar(Long solicitacaoId) {
        SolicitacaoDesligamento solicitacao = pendente(solicitacaoId);
        Long assistidoId = solicitacao.getAssistido().getAssistidoId();
        solicitacao.decidir(StatusSolicitacaoDesligamento.APROVADA, null, usuarioAutenticadoService.getUsuarioAtual());
        repository.saveAndFlush(solicitacao);
        assistidoService.alterarStatus(assistidoId,
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, solicitacao.getDataSaida(), solicitacao.getMotivo()));
        registrar(solicitacao, "Pedido de desligamento aprovado");
        return SolicitacaoDesligamentoDTO.de(repository.findById(solicitacaoId).orElseThrow());
    }

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public SolicitacaoDesligamentoDTO recusar(Long solicitacaoId, String resposta) {
        SolicitacaoDesligamento solicitacao = pendente(solicitacaoId);
        solicitacao.decidir(StatusSolicitacaoDesligamento.RECUSADA, resposta.trim(), usuarioAutenticadoService.getUsuarioAtual());
        registrar(solicitacao, "Pedido de desligamento recusado");
        return SolicitacaoDesligamentoDTO.de(solicitacao);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoDesligamentoDTO> listar() {
        return repository.findPendentesERecentes(OffsetDateTime.now().minusDays(DIAS_DECISOES_RECENTES)).stream()
                .map(SolicitacaoDesligamentoDTO::de)
                .toList();
    }

    private SolicitacaoDesligamento pendente(Long solicitacaoId) {
        SolicitacaoDesligamento solicitacao = repository.findById(solicitacaoId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Pedido de desligamento não encontrado: id " + solicitacaoId));
        if (!solicitacao.pendente()) {
            throw new RegraNegocioException("Este pedido de desligamento já foi decidido.");
        }
        return solicitacao;
    }

    private void registrar(SolicitacaoDesligamento solicitacao, String descricao) {
        auditoriaService.registrar(ModuloSistema.ASSISTIDOS, "solicitacao_desligamento", solicitacao.getSolicitacaoId(),
                AcaoSistema.EDITAR, Map.of("status", "PENDENTE"), Map.of("status", solicitacao.getStatus().name(),
                        "assistidoId", solicitacao.getAssistido().getAssistidoId()), descricao);
    }
}
