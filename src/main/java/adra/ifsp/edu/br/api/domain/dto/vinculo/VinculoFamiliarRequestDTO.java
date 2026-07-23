package adra.ifsp.edu.br.api.domain.dto.vinculo;

import jakarta.validation.constraints.NotNull;

public record VinculoFamiliarRequestDTO(

        // No POST (criacao), vem do corpo. No PUT (edicao), o responsavelId
        // do path prevalece - ver VinculoFamiliarController.
        @NotNull(message = "Responsavel e' obrigatorio")
        Long responsavelId,

        String parentesco,

        boolean responsavelPrincipal,

        boolean contatoEmergencia,

        boolean autorizadoRetirada,

        String observacoes
) {
}
