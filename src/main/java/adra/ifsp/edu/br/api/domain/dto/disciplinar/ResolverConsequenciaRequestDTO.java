package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * Fecha uma consequência pendente: a conversa com o responsável (3ª de
 * uniforme) ou o passeio perdido (3ª suspensão). A observação registra quem
 * compareceu / qual passeio.
 */
public record ResolverConsequenciaRequestDTO(
        @NotNull(message = "Informe a data.")
        @PastOrPresent(message = "A data não pode ser no futuro.")
        LocalDate data,

        @NotBlank(message = "Descreva como foi resolvido (ex.: quem compareceu à conversa ou qual passeio foi perdido).")
        String observacao
) {
}
