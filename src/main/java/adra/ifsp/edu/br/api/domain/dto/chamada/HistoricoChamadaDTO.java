package adra.ifsp.edu.br.api.domain.dto.chamada;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.time.LocalDate;

public record HistoricoChamadaDTO(
        LocalDate data,
        StatusPresenca statusPresenca
) {
}
