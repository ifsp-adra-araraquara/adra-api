package adra.ifsp.edu.br.api.domain.dto.matricula;

import java.util.List;

/**
 * O que acontece ao encerrar o ano, antes de confirmar: quem renovou segue para
 * o ano seguinte; quem não renovou perde a vaga (é desligado no último dia do ano).
 */
public record EncerramentoPreviaDTO(
        int ano,
        int totalAtivas,
        int renovaram,
        int naoRenovaram,
        /** Famílias novas já matriculadas no ano seguinte. */
        int novosNoAnoSeguinte,
        List<AtendidoNaoRenovouDTO> quemNaoRenovou,
        boolean podeEncerrar,
        /** Por que ainda não dá para encerrar (null quando pode). */
        String motivoBloqueio
) {
    public record AtendidoNaoRenovouDTO(Long assistidoId, String nomeCompleto, String nomeTurma) {
    }
}
