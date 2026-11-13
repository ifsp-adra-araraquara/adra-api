package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ApiApplication;
import adra.ifsp.edu.br.api.domain.dto.notificacao.ResultadoProcessamentoDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.SituacaoEnvioDTO;
import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * FT-14: uma vez por dia gera as notificações do dia, manda o resumo por e-mail
 * (um por pessoa) e apaga as que passaram da retenção. O Administrador pode
 * rodar na hora pela tela de notificações; dá no mesmo, porque nada se repete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoJob {

    private final GeradorNotificacoes gerador;
    private final NotificacaoService notificacaoService;
    private final CanalEmail canalEmail;

    @Scheduled(cron = "${adra.notificacao.cron:0 0 7 * * *}", zone = "America/Sao_Paulo")
    public void executar() {
        processar();
    }

    /** synchronized: o agendado e o "processar agora" não rodam ao mesmo tempo. */
    public synchronized ResultadoProcessamentoDTO processar() {
        int geradas = gerador.gerar(LocalDate.now(ApiApplication.FUSO));
        int enviadas = notificacaoService.entregarPendentes();
        int apagadas = notificacaoService.apagarAntigas();
        log.info("Notificações: {} nova(s), {} mensagem(ns) enviada(s), {} apagada(s) pela retenção.",
                geradas, enviadas, apagadas);
        return new ResultadoProcessamentoDTO(geradas, enviadas);
    }

    public SituacaoEnvioDTO situacao() {
        return new SituacaoEnvioDTO(canalEmail.ligado(), canalEmail.limiteDiario(), canalEmail.enviadosHoje(),
                notificacaoService.pendentes(CanalNotificacao.EMAIL));
    }
}
