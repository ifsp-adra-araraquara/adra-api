package adra.ifsp.edu.br.api.domain.dto.turma;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.List;

/**
 * Corrige quando os atendidos entraram no grupo (ex.: ligados ao grupo em 10/11, mas
 * frequentam desde 01/11). Sem `assistidoIds`, vale para todos os que estão no grupo hoje.
 */
public record CorrecaoDataEntradaRequestDTO(
        @NotNull(message = "Informe a data de entrada no grupo.")
        @PastOrPresent(message = "A data de entrada no grupo não pode ser no futuro.")
        LocalDate dataEntrada,

        List<Long> assistidoIds
) {
}
