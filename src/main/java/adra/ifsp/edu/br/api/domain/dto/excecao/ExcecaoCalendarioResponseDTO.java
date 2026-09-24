package adra.ifsp.edu.br.api.domain.dto.excecao;

import adra.ifsp.edu.br.api.domain.enums.TipoExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.model.ExcecaoCalendario;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExcecaoCalendarioResponseDTO(
        Long excecaoId,
        LocalDate dataExcecao,
        TipoExcecaoCalendario tipo,
        String descricao,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static ExcecaoCalendarioResponseDTO fromEntity(ExcecaoCalendario entity) {
        return new ExcecaoCalendarioResponseDTO(
                entity.getExcecaoId(),
                entity.getDataExcecao(),
                entity.getTipo(),
                entity.getDescricao(),
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }
}
