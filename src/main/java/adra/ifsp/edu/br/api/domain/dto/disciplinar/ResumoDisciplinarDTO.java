package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import java.time.LocalDate;

/**
 * Situação disciplinar atual do assistido (topo da aba Disciplinar da ficha).
 * Os "faltam" dizem quantos registros do nível faltam para a próxima
 * consequência (3, 2 ou 1).
 */
public record ResumoDisciplinarDTO(
        long ocorrenciasAtivas,
        long advertenciasAtivas,
        long suspensoesAtivas,
        long uniformeAtivas,
        int faltamParaAdvertencia,
        int faltamParaSuspensao,
        int faltamParaPerderPasseio,
        int faltamParaRetornoComResponsavel,
        /** Fim da suspensão em vigor (hoje ou futura), ou null. */
        LocalDate suspensoAte,
        boolean retornoComResponsavelPendente,
        boolean perdeProximoPasseio,
        long documentosPendentes
) {
}
