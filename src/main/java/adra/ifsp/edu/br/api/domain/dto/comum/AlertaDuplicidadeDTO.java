package adra.ifsp.edu.br.api.domain.dto.comum;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;

import java.util.List;

public record AlertaDuplicidadeDTO(
        String mensagem,
        List<AssistidoResponseDTO> possiveisDuplicados
) {
}
