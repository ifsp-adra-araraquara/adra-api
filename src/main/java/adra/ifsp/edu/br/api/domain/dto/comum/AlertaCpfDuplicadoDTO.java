package adra.ifsp.edu.br.api.domain.dto.comum;

import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelResponseDTO;

public record AlertaCpfDuplicadoDTO (
      String mensagem,
      ResponsavelResponseDTO responsavelExistente
  ) {
}