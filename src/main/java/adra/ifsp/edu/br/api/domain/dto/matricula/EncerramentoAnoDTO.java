package adra.ifsp.edu.br.api.domain.dto.matricula;

import java.time.OffsetDateTime;

import adra.ifsp.edu.br.api.domain.model.EncerramentoAno;

/** Ano encerrado: quando, por quem e o resumo. */
public record EncerramentoAnoDTO(int ano, OffsetDateTime encerradoEm, String encerradoPorNome,
                                 int totalEncerradas, int totalRenovaram, int totalNaoRenovaram) {

    public static EncerramentoAnoDTO de(EncerramentoAno e) {
        return new EncerramentoAnoDTO(e.getAno(), e.getEncerradoEm(),
                e.getEncerradoPor() != null ? e.getEncerradoPor().getNomeCompleto() : null,
                e.getTotalEncerradas(), e.getTotalRenovaram(), e.getTotalNaoRenovaram());
    }
}
