package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegistroDisciplinarRequestDTO(
        /** Ocorrência, advertência ou suspensão. Obrigatório quando não vem `motivoId`. */
        NivelDisciplinar nivel,

        /** Motivo escrito à mão. Obrigatório quando não vem `motivoId`. */
        @Size(max = 200, message = "O motivo tem no máximo 200 caracteres.")
        String motivo,

        /** Motivo cadastrado (registros antigos); aceito por compatibilidade. */
        Long motivoId,

        @NotNull(message = "Informe a data.")
        @PastOrPresent(message = "A data não pode ser no futuro.")
        LocalDate data,

        @NotBlank(message = "Descreva o que aconteceu.")
        String descricao,

        /** Opcional: grupo em que aconteceu (vem preenchido quando registrado pela chamada). */
        Long turmaId,

        /** Só para suspensão; sem valor, vale de `data` até `data` + 2 dias (3 dias). */
        LocalDate suspensaoInicio,
        LocalDate suspensaoFim
) {
}
