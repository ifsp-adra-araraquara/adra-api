package adra.ifsp.edu.br.api.domain.dto.excecao;

import adra.ifsp.edu.br.api.domain.enums.TipoExcecaoCalendario;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExcecaoCalendarioRequestDTO(

        @NotNull(message = "Informe a data da exceção.")
        LocalDate dataExcecao,

        @NotNull(message = "Informe o tipo da exceção.")
        TipoExcecaoCalendario tipo,

        @Size(max = 150, message = "A descrição deve ter no máximo 150 caracteres.")
        String descricao
) {
}
