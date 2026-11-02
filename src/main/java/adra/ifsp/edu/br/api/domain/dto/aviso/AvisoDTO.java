package adra.ifsp.edu.br.api.domain.dto.aviso;

import adra.ifsp.edu.br.api.domain.enums.TipoAviso;

import java.time.LocalDate;

/**
 * Um aviso da central (GET /api/avisos). `assistidoId`, `turmaId` e `data`
 * apontam o item onde o aviso se resolve; avisos de contagem vêm sem eles.
 */
public record AvisoDTO(
        TipoAviso tipo,
        String titulo,
        String detalhe,
        Long assistidoId,
        Long turmaId,
        LocalDate data
) {
}
