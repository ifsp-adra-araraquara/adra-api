package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.model.FaltaJustificada;
import adra.ifsp.edu.br.api.domain.model.Presenca;

import java.time.LocalDateTime;

public record PresencaResponseDTO(
        Long presencaId,
        Long aulaId,
        Long assistidoId,
        // CA-66.1: front monta a lista de alunos da chamada só com este DTO
        // (GET /api/chamadas/aula/{id}), sem precisar de um segundo request a
        // /api/assistidos — evita ter duas fontes de roster desalinhadas.
        String nomeCompleto,
        StatusPresenca statusPresenca,
        MotivoFalta motivoFalta,
        String observacao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {

    /** Presenca sem falta justificada associada (status FALTA). */
    public static PresencaResponseDTO fromEntity(Presenca presenca) {
        return fromEntity(presenca, null);
    }

    /**
     * Modelo esparso: PRESENTE nunca vira linha na tabela presenca, então não
     * existe uma Presenca pra montar esse DTO — é só a ausência de registro de
     * falta pra esse (aula, assistido). presencaId/criadoEm/atualizadoEm ficam null
     * de propósito, pra deixar claro pro consumidor que isso é inferido, não persistido.
     */
    public static PresencaResponseDTO presente(Long aulaId, Long assistidoId, String nomeCompleto) {
        return new PresencaResponseDTO(
                null, aulaId, assistidoId, nomeCompleto, StatusPresenca.PRESENTE, null, null, null, null
        );
    }

    public static PresencaResponseDTO fromEntity(Presenca presenca, FaltaJustificada faltaJustificada) {
        return new PresencaResponseDTO(
                presenca.getPresencaId(),
                presenca.getAula() != null ? presenca.getAula().getAulaId() : null,
                presenca.getAssistido() != null ? presenca.getAssistido().getAssistidoId() : null,
                presenca.getAssistido() != null ? presenca.getAssistido().getNomeCompleto() : null,
                presenca.getStatusPresenca(),
                faltaJustificada != null ? faltaJustificada.getMotivoFalta() : null,
                faltaJustificada != null ? faltaJustificada.getObservacao() : null,
                presenca.getCriadoEm(),
                presenca.getAtualizadoEm()
        );
    }
}
