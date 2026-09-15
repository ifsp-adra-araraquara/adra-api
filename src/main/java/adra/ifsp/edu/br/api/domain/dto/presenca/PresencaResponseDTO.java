package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.model.Presenca;

import java.time.LocalDateTime;

public record PresencaResponseDTO(
        Long presencaId,
        Long aulaId,
        Long assistidoId,
        StatusPresenca statusPresenca,
        String justificativaFalta,
        String observacaoDoDia,
        LocalDateTime horarioRegistro
) {

    public static PresencaResponseDTO fromEntity(Presenca presenca) {
        return new PresencaResponseDTO(
                presenca.getPresencaId(),
                presenca.getAula() != null ? presenca.getAula().getAulaId() : null,
                presenca.getAssistido() != null ? presenca.getAssistido().getAssistidoId() : null,
                presenca.getStatusPresenca(),
                presenca.getJustificativaFalta(),
                presenca.getObservacaoDoDia(),
                presenca.getHorarioRegistro()
        );
    }
}
