package adra.ifsp.edu.br.api.domain.dto.oficina;

import java.util.List;

public record OficinaResponseDTO(
        Long oficinaId,
        String nomeOficina,
        Long oficineiroResponsavelId,
        String local,
        Boolean ativo,
        List<Long> turmaIds
) {
}
