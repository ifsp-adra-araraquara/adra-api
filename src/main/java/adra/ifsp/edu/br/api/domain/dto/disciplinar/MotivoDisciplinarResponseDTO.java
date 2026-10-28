package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;

public record MotivoDisciplinarResponseDTO(
        Long motivoId,
        String nome,
        NivelDisciplinar nivel,
        boolean ativo
) {
    public static MotivoDisciplinarResponseDTO de(MotivoDisciplinar m) {
        return new MotivoDisciplinarResponseDTO(m.getMotivoId(), m.getNome(), m.getNivel(), m.isAtivo());
    }
}
