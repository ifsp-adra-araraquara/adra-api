package adra.ifsp.edu.br.api.domain.dto.assistido;

import adra.ifsp.edu.br.api.domain.enums.StatusLinhaLote;

import java.util.List;
import java.util.Map;

public record ResultadoLinhaLoteDTO(
        int indice,
        StatusLinhaLote status,
        Long assistidoId,
        List<String> erros,
        Map<String, String> responsaveisExistentes
) {

    public static ResultadoLinhaLoteDTO valida(int indice, Map<String, String> responsaveisExistentes) {
        return new ResultadoLinhaLoteDTO(indice, StatusLinhaLote.VALIDA, null, List.of(), responsaveisExistentes);
    }

    public static ResultadoLinhaLoteDTO criada(int indice, Long assistidoId) {
        return new ResultadoLinhaLoteDTO(indice, StatusLinhaLote.CRIADA, assistidoId, List.of(), Map.of());
    }

    public static ResultadoLinhaLoteDTO invalida(int indice, List<String> erros, Map<String, String> responsaveisExistentes) {
        return new ResultadoLinhaLoteDTO(indice, StatusLinhaLote.INVALIDA, null, erros, responsaveisExistentes);
    }
}
