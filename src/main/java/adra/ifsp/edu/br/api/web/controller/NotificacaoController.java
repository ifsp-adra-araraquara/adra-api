package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.NotificacaoDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.ResultadoProcessamentoDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.SituacaoEnvioDTO;
import adra.ifsp.edu.br.api.domain.service.NotificacaoJob;
import adra.ifsp.edu.br.api.domain.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** FT-14: notificações guardadas do usuário logado e, para o Administrador, o envio por e-mail. */
@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
public class NotificacaoController {

    private static final int TAMANHO_MAXIMO = 100;

    private final NotificacaoService notificacaoService;
    private final NotificacaoJob notificacaoJob;

    @GetMapping
    public ResponseEntity<PaginaDTO<NotificacaoDTO>> minhas(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(notificacaoService.minhas(Math.max(pagina, 0), Math.clamp(tamanho, 1, TAMANHO_MAXIMO)));
    }

    @GetMapping("/nao-lidas")
    public ResponseEntity<Map<String, Long>> naoLidas() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(Map.of("total", notificacaoService.naoLidas()));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarComoLida(@PathVariable Long id) {
        notificacaoService.marcarComoLida(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lidas")
    public ResponseEntity<Void> marcarTodasComoLidas() {
        notificacaoService.marcarTodasComoLidas();
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    @GetMapping("/envio")
    public SituacaoEnvioDTO situacaoEnvio() {
        return notificacaoJob.situacao();
    }

    /** Roda o job agora (teste de fluxo do card). Não repete aviso: rodar duas vezes não manda dois e-mails. */
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    @PostMapping("/processar")
    public ResultadoProcessamentoDTO processar() {
        return notificacaoJob.processar();
    }
}
