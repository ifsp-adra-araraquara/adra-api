package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import java.time.LocalDate;

/**
 * Sinalização de um assistido numa data — usada pela chamada e pela
 * listagem de Assistidos. Só vem para quem tem alguma sinalização.
 */
public record SinalizacaoDisciplinarDTO(
        Long assistidoId,
        /** Fim da suspensão que cobre a data consultada, ou null. */
        LocalDate suspensoAte,
        boolean retornoComResponsavelPendente,
        boolean perdeProximoPasseio
) {
}
