package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import java.util.List;

/** GET /api/assistidos/{id}/disciplinar — resumo + histórico (mais recente primeiro). */
public record FichaDisciplinarDTO(
        ResumoDisciplinarDTO resumo,
        List<RegistroDisciplinarResponseDTO> registros
) {
}
