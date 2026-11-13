package adra.ifsp.edu.br.api.domain.dto.notificacao;

import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.enums.TipoEnvolvido;
import adra.ifsp.edu.br.api.domain.model.Notificacao;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/** FT-14: uma notificação do usuário logado (GET /api/notificacoes). */
public record NotificacaoDTO(
        Long notificacaoId,
        TipoAviso tipo,
        String titulo,
        String mensagem,
        String link,
        OffsetDateTime criadoEm,
        boolean lida,
        List<EnvolvidoDTO> envolvidos
) {
    public record EnvolvidoDTO(TipoEnvolvido tipo, Long id) {
    }

    public static NotificacaoDTO de(Notificacao n) {
        return new NotificacaoDTO(n.getNotificacaoId(), n.getTipo(), n.getTitulo(), n.getMensagem(), n.getLink(),
                n.getCriadoEm(), n.getLidaEm() != null,
                n.getEnvolvidos().stream()
                        .map(e -> new EnvolvidoDTO(e.tipo(), e.id()))
                        .sorted(Comparator.comparing(EnvolvidoDTO::tipo).thenComparing(EnvolvidoDTO::id))
                        .toList());
    }
}
