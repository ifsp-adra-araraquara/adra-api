package adra.ifsp.edu.br.api.domain.dto.responsavel;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;

/**
 * Um assistido ligado ao responsável, do ponto de vista do responsável —
 * seção "Assistidos vinculados" da ficha do responsável.
 */
public record AssistidoVinculadoDTO(
        Long assistidoId,
        String nomeCompleto,
        StatusGeral status,
        String nomeTurma,
        String parentesco,
        boolean responsavelPrincipal,
        boolean contatoEmergencia,
        boolean autorizadoRetirada
) {
}
