package adra.ifsp.edu.br.api.domain.dto.excecao;

import adra.ifsp.edu.br.api.domain.enums.TipoExcecaoCalendario;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExcecaoCalendarioRequestDTO(

        @NotNull(message = "Informe a data da exceção.")
        LocalDate dataExcecao,

        // Opcional: último dia de um período (ex.: férias). Null = só dataExcecao.
        LocalDate dataFim,

        @NotNull(message = "Informe o tipo da exceção.")
        TipoExcecaoCalendario tipo,

        @Size(max = 150, message = "A descrição deve ter no máximo 150 caracteres.")
        String descricao
) {

    /** Limite de um período lançado de uma vez (evita gerar milhares de linhas por engano). */
    public static final int MAXIMO_DIAS_PERIODO = 120;

    @AssertTrue(message = "A data final deve ser igual ou posterior à inicial e o período ter no máximo 120 dias.")
    public boolean isPeriodoValido() {
        if (dataFim == null || dataExcecao == null) {
            return true;
        }
        return !dataFim.isBefore(dataExcecao) && !dataFim.isAfter(dataExcecao.plusDays(MAXIMO_DIAS_PERIODO - 1));
    }
}
