package adra.ifsp.edu.br.api.domain.dto.precadastro;

import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PreCadastroResponseDTO(
        Long preCadastroId,
        String nomeCompleto,
        LocalDate dataNascimento,
        String responsavelNome,
        String responsavelTelefone,
        String endereco,
        String observacao,
        StatusPreCadastro status,
        String revisadoPorNome,
        OffsetDateTime revisadoEm,
        OffsetDateTime criadoEm
) {
    public static PreCadastroResponseDTO de(PreCadastro p) {
        return new PreCadastroResponseDTO(
                p.getPreCadastroId(),
                p.getNomeCompleto(),
                p.getDataNascimento(),
                p.getResponsavelNome(),
                p.getResponsavelTelefone(),
                p.getEndereco(),
                p.getObservacao(),
                p.getStatus(),
                p.getRevisadoPor() != null ? p.getRevisadoPor().getNomeCompleto() : null,
                p.getRevisadoEm(),
                p.getCriadoEm());
    }
}
