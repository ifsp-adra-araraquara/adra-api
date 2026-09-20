package adra.ifsp.edu.br.api.domain.dto.aula;

import java.util.List;

public record GerarAulasResponseDTO(
        int aulasCriadas,
        int aulasExcecaoPuladas,
        int aulasDuplicadasPuladas,
        List<AulaResponseDTO> aulas
) {
}
