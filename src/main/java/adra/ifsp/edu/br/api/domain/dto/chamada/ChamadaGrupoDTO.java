package adra.ifsp.edu.br.api.domain.dto.chamada;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** GET e PUT /api/chamadas/turma/{id}/dia/{data}: a chamada do grupo no dia, com quem estava no grupo naquela data. */
public record ChamadaGrupoDTO(
        Long turmaId,
        String nomeTurma,
        LocalDate data,
        boolean lancada,
        boolean podeEditar,
        String lancadaPorNome,
        LocalDateTime lancadaEm,
        String atualizadaPorNome,
        LocalDateTime atualizadaEm,
        List<AtendidoChamadaDTO> atendidos
) {
}
