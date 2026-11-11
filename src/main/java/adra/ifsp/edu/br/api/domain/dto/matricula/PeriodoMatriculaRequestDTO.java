package adra.ifsp.edu.br.api.domain.dto.matricula;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Abre ou ajusta o período do ano. {@code fim} vazio = aberto até fechar. */
public record PeriodoMatriculaRequestDTO(
        @NotNull(message = "Informe a data de início das inscrições.")
        LocalDate inicio,
        LocalDate fim
) {
}
