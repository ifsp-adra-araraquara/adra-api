package adra.ifsp.edu.br.api.domain.dto.faixaetaria;

import adra.ifsp.edu.br.api.domain.model.FaixaEtaria;

public record FaixaEtariaResponseDTO(
        Long faixaEtariaId,
        Short idadeMinima,
        Short idadeMaxima,
        String rotulo,
        boolean ativo
) {
    public static FaixaEtariaResponseDTO de(FaixaEtaria f) {
        return new FaixaEtariaResponseDTO(f.getFaixaEtariaId(), f.getIdadeMinima(), f.getIdadeMaxima(),
                f.getRotulo(), f.isAtivo());
    }
}
