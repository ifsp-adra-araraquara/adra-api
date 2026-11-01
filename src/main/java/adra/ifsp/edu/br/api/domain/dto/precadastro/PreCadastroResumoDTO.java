package adra.ifsp.edu.br.api.domain.dto.precadastro;

import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;

/** Linha da lista de pré-cadastros da equipe. */
public record PreCadastroResumoDTO(
        Long preCadastroId,
        String nomeCompleto,
        LocalDate dataNascimento,
        Integer idade,
        String responsavelNome,
        String responsavelTelefone,
        OffsetDateTime criadoEm,
        StatusPreCadastro status,
        boolean cpfJaCadastrado,
        Long assistidoId,
        List<String> anexosFaltando
) {
    public static PreCadastroResumoDTO de(PreCadastro p, boolean cpfJaCadastrado, List<String> anexosFaltando,
                                          LocalDate hoje) {
        return new PreCadastroResumoDTO(
                p.getPreCadastroId(),
                p.getNomeCompleto(),
                p.getDataNascimento(),
                p.getDataNascimento() != null ? Period.between(p.getDataNascimento(), hoje).getYears() : null,
                p.getResponsavelNome(),
                p.getResponsavelTelefone(),
                p.getCriadoEm(),
                p.getStatus(),
                cpfJaCadastrado,
                p.getAssistidoId(),
                anexosFaltando);
    }
}
