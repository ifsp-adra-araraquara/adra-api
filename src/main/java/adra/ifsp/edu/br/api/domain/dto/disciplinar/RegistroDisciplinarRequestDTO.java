package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record RegistroDisciplinarRequestDTO(
        @NotNull(message = "Informe o motivo.")
        Long motivoId,

        @NotNull(message = "Informe a data.")
        @PastOrPresent(message = "A data não pode ser no futuro.")
        LocalDate data,

        @NotBlank(message = "Descreva o que aconteceu.")
        String descricao,

        /** Opcional: aula em que aconteceu (vem preenchida quando registrado pela chamada). */
        Long aulaId,

        /** Só para suspensão; sem valor, vale de `data` até `data` + 2 dias (3 dias). */
        LocalDate suspensaoInicio,
        LocalDate suspensaoFim
) {
}
