package adra.ifsp.edu.br.api.domain.dto.assistido;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AssistidoResponseDTO(
        Long assistidoId,
        String nomeCompleto,
        LocalDate dataNascimento,
        String cpf,
        LocalDate dataEntrada,
        LocalDate dataSaida,
        String motivoSaida,
        String necessidadesEspecificas,
        String observacoes,
        StatusGeral status,
        Integer totalOcorrenciasAtivas,
        Integer totalAdvertenciasAtivas,
        Integer totalSuspensoes,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
