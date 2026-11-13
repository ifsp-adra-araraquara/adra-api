package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.notificacao.PedidoNotificacao;
import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.enums.StatusEntrega;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.model.Notificacao;
import adra.ifsp.edu.br.api.domain.model.Notificacao.Envolvido;
import adra.ifsp.edu.br.api.domain.model.NotificacaoEntrega;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NotificacaoEntregaRepository;
import adra.ifsp.edu.br.api.domain.repository.NotificacaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FT-14: regras do NotificacaoService sem banco (o fluxo com Postgres está no NotificacaoIT). */
class NotificacaoServiceTest {

    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final NotificacaoEntregaRepository entregaRepository = mock(NotificacaoEntregaRepository.class);
    private final CanalFalso email = new CanalFalso();
    private final AtomicLong ids = new AtomicLong();

    private NotificacaoService service;
    private Usuario coordenadora;
    private Usuario coordenador;

    @BeforeEach
    void montar() {
        service = new NotificacaoService(notificacaoRepository, entregaRepository, mock(UsuarioRepository.class),
                mock(UsuarioAutenticadoService.class), List.of(new CanalPlataforma(), email), 180);
        coordenadora = usuario(1L, true);
        coordenador = usuario(2L, true);
        when(notificacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Uma notificação por destinatário; o sino entrega na hora e o e-mail espera o resumo")
    void umaPorDestinatario() {
        int novas = service.notificar(pedido("assistido:7:desde:2026-11-03", coordenadora, coordenador));

        assertThat(novas).isEqualTo(2);
        ArgumentCaptor<Notificacao> salvas = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(salvas.capture());
        assertThat(salvas.getAllValues()).extracting(n -> n.getDestinatario().getUsuarioId()).containsExactly(1L, 2L);
        Notificacao primeira = salvas.getAllValues().getFirst();
        assertThat(primeira.getEnvolvidos()).containsExactly(Envolvido.assistido(7L));
        assertThat(primeira.getEntregas())
                .extracting(NotificacaoEntrega::getCanal, NotificacaoEntrega::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(CanalNotificacao.PLATAFORMA, StatusEntrega.ENVIADA),
                        org.assertj.core.groups.Tuple.tuple(CanalNotificacao.EMAIL, StatusEntrega.PENDENTE));
        assertThat(email.envios).isEmpty();
    }

    @Test
    @DisplayName("O mesmo fato não chega duas vezes à mesma pessoa; inativo não recebe")
    void naoRepeteNemAvisaInativo() {
        when(notificacaoRepository.existsByTipoAndReferenciaAndDestinatario_UsuarioId(
                TipoAviso.FALTAS_SEGUIDAS, "assistido:7:desde:2026-11-03", 1L)).thenReturn(true);

        int novas = service.notificar(pedido("assistido:7:desde:2026-11-03", coordenadora, usuario(3L, false)));

        assertThat(novas).isZero();
        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CA-FT14.1: tudo o que é de uma pessoa sai em um e-mail só")
    void umResumoPorPessoa() {
        when(entregaRepository.pendentes(CanalNotificacao.EMAIL)).thenReturn(List.of(
                pendente(coordenadora, "Ana chegou a 3 faltas seguidas"),
                pendente(coordenador, "Ana chegou a 3 faltas seguidas"),
                pendente(coordenadora, "Semanário atrasado — Futebol")));

        int mensagens = service.entregarPendentes();

        assertThat(mensagens).isEqualTo(2);
        assertThat(email.envios).hasSize(2);
        assertThat(email.envios.getFirst()).containsExactly("Ana chegou a 3 faltas seguidas", "Semanário atrasado — Futebol");
        verify(entregaRepository, times(2)).marcar(anyCollection(), eq(StatusEntrega.ENVIADA), any());
    }

    @Test
    @DisplayName("CA-FT14.2: sem aviso novo, não sai e-mail")
    void semPendenteNaoEnvia() {
        when(entregaRepository.pendentes(CanalNotificacao.EMAIL)).thenReturn(List.of());

        assertThat(service.entregarPendentes()).isZero();
        assertThat(email.envios).isEmpty();
    }

    @Test
    @DisplayName("CA-FT14.3: com o envio desligado, nada é enviado e nada é consultado")
    void desligadoNaoEnvia() {
        email.ligado = false;

        assertThat(service.entregarPendentes()).isZero();
        assertThat(email.envios).isEmpty();
        verify(entregaRepository, never()).pendentes(any());
    }

    @Test
    @DisplayName("Limite do dia: quem não coube fica pendente para amanhã")
    void respeitaOLimite() {
        email.vagas = 1;
        when(entregaRepository.pendentes(CanalNotificacao.EMAIL)).thenReturn(List.of(
                pendente(coordenadora, "Aviso 1"), pendente(coordenador, "Aviso 2")));

        assertThat(service.entregarPendentes()).isEqualTo(1);
        assertThat(email.envios).hasSize(1);
    }

    @Test
    @DisplayName("Pendente velho demais ou de usuário inativo é descartado, não enviado")
    void descartaVelhoEInativo() {
        email.janela = Duration.ofDays(3);
        NotificacaoEntrega velha = pendente(coordenadora, "Aviso da semana passada");
        velha.getNotificacao().setCriadoEm(OffsetDateTime.now().minusDays(8));
        NotificacaoEntrega deInativo = pendente(usuario(3L, false), "Aviso de quem saiu");
        when(entregaRepository.pendentes(CanalNotificacao.EMAIL)).thenReturn(List.of(velha, deInativo));

        assertThat(service.entregarPendentes()).isZero();
        assertThat(email.envios).isEmpty();
        verify(entregaRepository).marcar(eq(List.of(velha.getEntregaId(), deInativo.getEntregaId())),
                eq(StatusEntrega.DESCARTADA), eq(null));
    }

    @Test
    @DisplayName("E-mail que falha continua pendente")
    void falhaFicaPendente() {
        email.falhar = true;
        when(entregaRepository.pendentes(CanalNotificacao.EMAIL)).thenReturn(List.of(pendente(coordenadora, "Aviso")));

        assertThat(service.entregarPendentes()).isZero();
        verify(entregaRepository, never()).marcar(anyList(), eq(StatusEntrega.ENVIADA), any());
    }

    private PedidoNotificacao pedido(String referencia, Usuario... destinatarios) {
        return new PedidoNotificacao(TipoAviso.FALTAS_SEGUIDAS, referencia, "Ana chegou a 3 faltas seguidas",
                "SI Manhã · última chamada em 10/11.", "/assistidos?assistidoId=7&aba=cham",
                Set.of(Envolvido.assistido(7L)), List.of(destinatarios), PedidoNotificacao.TODOS_OS_CANAIS);
    }

    private NotificacaoEntrega pendente(Usuario destinatario, String titulo) {
        Notificacao n = new Notificacao();
        n.setNotificacaoId(ids.incrementAndGet());
        n.setTipo(TipoAviso.FALTAS_SEGUIDAS);
        n.setDestinatario(destinatario);
        n.setTitulo(titulo);
        n.setCriadoEm(OffsetDateTime.now());
        NotificacaoEntrega e = new NotificacaoEntrega(n, CanalNotificacao.EMAIL);
        e.setEntregaId(n.getNotificacaoId());
        return e;
    }

    private static Usuario usuario(Long id, boolean ativo) {
        return Usuario.builder().usuarioId(id).nomeCompleto("Usuário " + id).email(id + "@teste.local").ativo(ativo).build();
    }

    /** Canal de resumo que só anota o que mandaria. */
    private static class CanalFalso implements CanalDeEntrega {
        boolean ligado = true;
        boolean falhar;
        int vagas = Integer.MAX_VALUE;
        Duration janela;
        final List<List<String>> envios = new ArrayList<>();

        @Override
        public CanalNotificacao canal() {
            return CanalNotificacao.EMAIL;
        }

        @Override
        public boolean imediato() {
            return false;
        }

        @Override
        public boolean ligado() {
            return ligado;
        }

        @Override
        public int vagasHoje() {
            return vagas;
        }

        @Override
        public Duration janela() {
            return janela;
        }

        @Override
        public boolean entregar(Usuario destinatario, List<Notificacao> notificacoes) {
            if (falhar) {
                return false;
            }
            envios.add(notificacoes.stream().map(Notificacao::getTitulo).toList());
            return true;
        }
    }
}
