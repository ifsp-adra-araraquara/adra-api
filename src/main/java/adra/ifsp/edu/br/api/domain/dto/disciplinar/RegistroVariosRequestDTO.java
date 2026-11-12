package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Um fato com um ou mais atendidos envolvidos: cria um registro por atendido. */
public record RegistroVariosRequestDTO(
        @NotEmpty(message = "Informe ao menos um atendido.")
        List<@NotNull Long> assistidoIds,

        NivelDisciplinar nivel,

        @Size(max = 200, message = "O motivo tem no máximo 200 caracteres.")
        String motivo,

        /** Motivo cadastrado (registros antigos); aceito por compatibilidade. */
        Long motivoId,

        @NotNull(message = "Informe a data.")
        @PastOrPresent(message = "A data não pode ser no futuro.")
        LocalDate data,

        @NotBlank(message = "Descreva o que aconteceu.")
        String descricao
) {
}
