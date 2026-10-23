package adra.ifsp.edu.br.api.domain.dto.assistido;

import java.math.BigDecimal;

/** Critérios de entrada no programa. {@code rendaFamiliarLimite} null = regra ainda não definida pela ADRA. */
public record ElegibilidadeDTO(BigDecimal rendaFamiliarLimite) {
}
