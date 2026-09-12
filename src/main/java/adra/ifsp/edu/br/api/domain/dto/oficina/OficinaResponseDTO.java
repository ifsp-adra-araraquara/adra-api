package adra.ifsp.edu.br.api.domain.dto.oficina;

import adra.ifsp.edu.br.api.domain.model.Oficina;

public record OficinaResponseDTO(
        Long oficinaId,
        String nomeOficina,
        String oficineiroResponsavel,
        Boolean ativo
) {

    /**
     * Fábrica de conveniência para converter a entidade em DTO de resposta,
     * evitando expor a entidade JPA diretamente pelo controller.
     */
    public static OficinaResponseDTO fromEntity(Oficina oficina) {
        return new OficinaResponseDTO(
                oficina.getOficinaId(),
                oficina.getNomeOficina(),
                oficina.getOficineiroResponsavel(),
                oficina.getAtivo()
        );
    }
}
