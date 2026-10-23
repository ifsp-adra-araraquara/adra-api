package adra.ifsp.edu.br.api.domain.dto.historico;

import java.time.LocalDate;

/** Um período de um oficineiro como responsável (dataFim null = atual). */
public record OficineiroHistoricoDTO(
        Long oficineiroId,
        String nomeOficineiro,
        LocalDate dataInicio,
        LocalDate dataFim
) {
}
