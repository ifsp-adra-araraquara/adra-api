package adra.ifsp.edu.br.api.domain.dto.vinculo;

import java.time.OffsetDateTime;

public record VinculoFamiliarResponseDTO(
        Long assistidoId,
        Long responsavelId,
        String nomeResponsavel,
        String cpfResponsavel,
        String telefoneResponsavel,
        String emailResponsavel,
        String parentesco,
        boolean responsavelPrincipal,
        boolean contatoEmergencia,
        boolean autorizadoRetirada,
        String observacoes,
        OffsetDateTime criadoEm
) {
}
