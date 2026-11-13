package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.NotificacaoDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.PedidoNotificacao;
import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusEntrega;
import adra.ifsp.edu.br.api.domain.model.Notificacao;
import adra.ifsp.edu.br.api.domain.model.NotificacaoEntrega;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NotificacaoEntregaRepository;
import adra.ifsp.edu.br.api.domain.repository.NotificacaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FT-14: notificações guardadas, agnósticas de tipo e de canal.
 *
 * <p>Quem precisa avisar alguém chama {@link #notificar(PedidoNotificacao)} com o
 * tipo, a referência do fato, o texto, os envolvidos, os destinatários e os canais.
 * Cada destinatário ganha a sua notificação; o mesmo (tipo, referência) não chega
 * duas vezes à mesma pessoa. Canais imediatos (a plataforma) entregam na hora; os
 * outros (e-mail) esperam {@link #entregarPendentes()}, que o job roda uma vez por
 * dia e junta tudo de uma pessoa em uma mensagem só.
 */
@Slf4j
@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoEntregaRepository entregaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final Map<CanalNotificacao, CanalDeEntrega> canais = new EnumMap<>(CanalNotificacao.class);
    private final int retencaoDias;

    public NotificacaoService(
            NotificacaoRepository notificacaoRepository,
            NotificacaoEntregaRepository entregaRepository,
            UsuarioRepository usuarioRepository,
            UsuarioAutenticadoService usuarioAutenticadoService,
            List<CanalDeEntrega> canais,
            @Value("${adra.notificacao.retencao-dias:180}") int retencaoDias) {
        this.notificacaoRepository = notificacaoRepository;
        this.entregaRepository = entregaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioAutenticadoService = usuarioAutenticadoService;
        canais.forEach(c -> this.canais.put(c.canal(), c));
        this.retencaoDias = retencaoDias;
    }

    /**
     * Cria uma notificação para cada destinatário ativo que ainda não recebeu
     * este (tipo, referência).
     *
     * @return quantas notificações novas foram criadas (0 = todos já sabiam)
     */
    @Transactional
    public int notificar(PedidoNotificacao pedido) {
        int novas = 0;
        for (Usuario destinatario : pedido.destinatarios()) {
            if (!destinatario.isAtivo() || notificacaoRepository.existsByTipoAndReferenciaAndDestinatario_UsuarioId(
                    pedido.tipo(), pedido.referencia(), destinatario.getUsuarioId())) {
                continue;
            }
            Notificacao notificacao = new Notificacao();
            notificacao.setTipo(pedido.tipo());
            notificacao.setReferencia(pedido.referencia());
            notificacao.setDestinatario(destinatario);
            notificacao.setTitulo(limitar(pedido.titulo(), 200));
            notificacao.setMensagem(limitar(pedido.mensagem(), 500));
            notificacao.setLink(pedido.link());
            if (pedido.envolvidos() != null) {
                notificacao.getEnvolvidos().addAll(pedido.envolvidos());
            }
            for (CanalNotificacao canal : pedido.canais()) {
                notificacao.getEntregas().add(new NotificacaoEntrega(notificacao, canal));
            }
            notificacaoRepository.save(notificacao);
            entregarImediatas(destinatario, notificacao);
            novas++;
        }
        return novas;
    }

    /** Usuários ativos de um perfil: o destinatário mais comum de um aviso. */
    @Transactional(readOnly = true)
    public List<Usuario> comPerfil(NomeNivelPermissao perfil) {
        return usuarioRepository.findByNivelPermissao_NomeAndAtivoTrue(perfil);
    }

    private void entregarImediatas(Usuario destinatario, Notificacao notificacao) {
        for (NotificacaoEntrega entrega : notificacao.getEntregas()) {
            CanalDeEntrega canal = canais.get(entrega.getCanal());
            if (canal != null && canal.imediato() && canal.ligado() && canal.entregar(destinatario, List.of(notificacao))) {
                entrega.setStatus(StatusEntrega.ENVIADA);
                entrega.setEnviadaEm(OffsetDateTime.now());
            }
        }
    }

    /**
     * Entrega o que está pendente nos canais que não são imediatos: uma mensagem
     * por destinatário, com tudo o que é dele. Sem transação de propósito: o envio
     * pode demorar, e cada destinatário é marcado assim que a mensagem sai.
     *
     * @return quantas mensagens saíram
     */
    public int entregarPendentes() {
        int mensagens = 0;
        for (CanalDeEntrega canal : canais.values()) {
            if (canal.imediato()) {
                continue;
            }
            if (!canal.ligado()) {
                log.info("Canal {} desligado: nada sai.", canal.canal());
                continue;
            }
            mensagens += entregarPendentes(canal);
        }
        return mensagens;
    }

    private int entregarPendentes(CanalDeEntrega canal) {
        OffsetDateTime agora = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        Duration janela = canal.janela();
        OffsetDateTime expiraAntes = janela == null ? null : agora.minus(janela);

        Map<Long, List<NotificacaoEntrega>> porDestinatario = new LinkedHashMap<>();
        List<Long> descartadas = new ArrayList<>();
        for (NotificacaoEntrega entrega : entregaRepository.pendentes(canal.canal())) {
            Notificacao n = entrega.getNotificacao();
            if (!n.getDestinatario().isAtivo() || (expiraAntes != null && n.getCriadoEm().isBefore(expiraAntes))) {
                descartadas.add(entrega.getEntregaId());
            } else {
                porDestinatario.computeIfAbsent(n.getDestinatario().getUsuarioId(), id -> new ArrayList<>()).add(entrega);
            }
        }
        if (!descartadas.isEmpty()) {
            entregaRepository.marcar(descartadas, StatusEntrega.DESCARTADA, null);
        }

        int vagas = canal.vagasHoje();
        int enviadas = 0;
        for (List<NotificacaoEntrega> doDestinatario : porDestinatario.values()) {
            if (enviadas >= vagas) {
                log.warn("Limite diário do canal {} atingido; {} destinatário(s) ficam para amanhã.",
                        canal.canal(), porDestinatario.size() - enviadas);
                break;
            }
            Usuario destinatario = doDestinatario.getFirst().getNotificacao().getDestinatario();
            List<Notificacao> notificacoes = doDestinatario.stream().map(NotificacaoEntrega::getNotificacao).toList();
            if (canal.entregar(destinatario, notificacoes)) {
                entregaRepository.marcar(doDestinatario.stream().map(NotificacaoEntrega::getEntregaId).toList(),
                        StatusEntrega.ENVIADA, agora);
                enviadas++;
            }
        }
        return enviadas;
    }

    /** Apaga as notificações mais velhas que a retenção: o título traz o nome do atendido. */
    @Transactional
    public int apagarAntigas() {
        OffsetDateTime limite = OffsetDateTime.now().minusDays(retencaoDias);
        notificacaoRepository.apagarEntregasAntesDe(limite);
        notificacaoRepository.apagarEnvolvidosAntesDe(limite);
        return notificacaoRepository.apagarAntesDe(limite);
    }

    /* ======================= Do usuário logado ======================= */

    @Transactional(readOnly = true)
    public PaginaDTO<NotificacaoDTO> minhas(int pagina, int tamanho) {
        Long usuarioId = usuarioAutenticadoService.getUsuarioAtual().getUsuarioId();
        return PaginaDTO.de(notificacaoRepository
                .doDestinatario(usuarioId, CanalNotificacao.PLATAFORMA, PageRequest.of(pagina, tamanho))
                .map(NotificacaoDTO::de));
    }

    @Transactional(readOnly = true)
    public long naoLidas() {
        return notificacaoRepository.contarNaoLidas(usuarioAutenticadoService.getUsuarioAtual().getUsuarioId(),
                CanalNotificacao.PLATAFORMA);
    }

    /** Só a dona marca: notificação de outra pessoa responde como não encontrada. */
    @Transactional
    public void marcarComoLida(Long notificacaoId) {
        Long usuarioId = usuarioAutenticadoService.getUsuarioAtual().getUsuarioId();
        Notificacao notificacao = notificacaoRepository.findByNotificacaoIdAndDestinatario_UsuarioId(notificacaoId, usuarioId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Notificação não encontrada: id " + notificacaoId));
        if (notificacao.getLidaEm() == null) {
            notificacao.setLidaEm(OffsetDateTime.now());
        }
    }

    @Transactional
    public int marcarTodasComoLidas() {
        return notificacaoRepository.marcarTodasComoLidas(usuarioAutenticadoService.getUsuarioAtual().getUsuarioId(),
                OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public long pendentes(CanalNotificacao canal) {
        return entregaRepository.countByCanalAndStatus(canal, StatusEntrega.PENDENTE);
    }

    private static String limitar(String texto, int maximo) {
        if (texto == null || texto.length() <= maximo) {
            return texto;
        }
        return texto.substring(0, maximo - 1) + "…";
    }
}
