package adra.ifsp.edu.br.api.domain.dto.disciplinar;

/**
 * Resposta de registrar: o registro lançado e a recomendação pendente do
 * assistido (o registro que completou 3 do nível), ou null.
 */
public record RegistroCriadoDTO(
        RegistroDisciplinarResponseDTO registro,
        RegistroDisciplinarResponseDTO recomendacao
) {
}
