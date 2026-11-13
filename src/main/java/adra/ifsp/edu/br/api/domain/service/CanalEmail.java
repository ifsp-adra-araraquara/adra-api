package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ApiApplication;
import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.model.Notificacao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NotificacaoEntregaRepository;
import adra.ifsp.edu.br.api.infra.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * FT-14: um e-mail por pessoa por dia, com tudo o que ficou pendente para ela.
 * O Brevo gratuito manda 300 por dia, contando a redefinição de senha e os
 * e-mails do Supabase Auth; por isso o limite daqui fica abaixo disso.
 */
@Slf4j
@Component
public class CanalEmail implements CanalDeEntrega {

    private final EmailService emailService;
    private final NotificacaoEntregaRepository entregaRepository;
    private final boolean ligado;
    private final int limiteDiario;
    private final Duration janela;

    public CanalEmail(
            EmailService emailService,
            NotificacaoEntregaRepository entregaRepository,
            @Value("${adra.notificacao.email.ligado:false}") boolean ligado,
            @Value("${adra.notificacao.email.limite-diario:250}") int limiteDiario,
            @Value("${adra.notificacao.email.janela-dias:3}") int janelaDias) {
        this.emailService = emailService;
        this.entregaRepository = entregaRepository;
        this.ligado = ligado;
        this.limiteDiario = limiteDiario;
        this.janela = Duration.ofDays(janelaDias);
    }

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

    public int limiteDiario() {
        return limiteDiario;
    }

    /** Mensagens (não notificações) que saíram hoje, no fuso da ADRA. */
    public long enviadosHoje() {
        return entregaRepository.mensagensDesde(CanalNotificacao.EMAIL.name(),
                LocalDate.now(ApiApplication.FUSO).atStartOfDay(ApiApplication.FUSO).toOffsetDateTime());
    }

    @Override
    public int vagasHoje() {
        return (int) Math.max(0, limiteDiario - enviadosHoje());
    }

    @Override
    public Duration janela() {
        return janela;
    }

    @Override
    public boolean entregar(Usuario destinatario, List<Notificacao> notificacoes) {
        try {
            emailService.enviarResumoNotificacoes(destinatario.getEmail(), destinatario.getNomeCompleto(),
                    notificacoes.stream()
                            .map(n -> new EmailService.ItemResumo(n.getTitulo(), n.getMensagem(), n.getLink()))
                            .toList());
            return true;
        } catch (RuntimeException e) {
            log.warn("Resumo de notificações não saiu para o usuário {}: {}", destinatario.getUsuarioId(), e.getMessage());
            return false;
        }
    }
}
