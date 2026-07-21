package adra.ifsp.edu.br.api.domain.dto.assistido;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Endpoint separado do PUT de dados cadastrais de proposito: mudar o status
 * para INATIVO e' uma decisao de negocio (saida do assistido), nao uma
 * edicao de cadastro comum, e exige data/motivo de saida.
 */
public record AssistidoStatusRequestDTO(
        @NotNull(message = "Status e' obrigatorio")
        StatusGeral status,

        LocalDate dataSaida,

        String motivoSaida
) {
}
