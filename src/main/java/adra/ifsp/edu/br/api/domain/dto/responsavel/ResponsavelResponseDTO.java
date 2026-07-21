package adra.ifsp.edu.br.api.domain.dto.responsavel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ResponsavelResponseDTO(
        Long responsavelId,
        String nomeCompleto,
        LocalDate dataNascimento,
        String cpf,
        String telefone,
        String email,
        String endereco,
        String observacoes,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
