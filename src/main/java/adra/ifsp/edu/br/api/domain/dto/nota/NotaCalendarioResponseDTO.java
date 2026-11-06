package adra.ifsp.edu.br.api.domain.dto.nota;

import adra.ifsp.edu.br.api.domain.model.NotaCalendario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record NotaCalendarioResponseDTO(
        Long notaId,
        LocalDate dataNota,
        LocalTime horario,
        String titulo,
        String descricao,
        Long criadoPorId,
        String criadoPorNome,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static NotaCalendarioResponseDTO fromEntity(NotaCalendario entity) {
        return new NotaCalendarioResponseDTO(
                entity.getNotaId(),
                entity.getDataNota(),
                entity.getHorario(),
                entity.getTitulo(),
                entity.getDescricao(),
                entity.getCriadoPor() != null ? entity.getCriadoPor().getUsuarioId() : null,
                entity.getCriadoPor() != null ? entity.getCriadoPor().getNomeCompleto() : null,
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }
}
