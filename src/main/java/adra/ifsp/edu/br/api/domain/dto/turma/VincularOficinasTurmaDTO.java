package adra.ifsp.edu.br.api.domain.dto.turma;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Oficinas que passam a atender o grupo; as que ficarem de fora deixam de atender. */
public record VincularOficinasTurmaDTO(
        @NotNull(message = "Informe as oficinas.")
        List<Long> oficinaIds
) {
}
