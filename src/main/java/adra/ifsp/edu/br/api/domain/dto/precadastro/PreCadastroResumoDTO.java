package adra.ifsp.edu.br.api.domain.dto.precadastro;

import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.Set;

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
        List<String> anexosFaltando,
        BigDecimal rendaFamiliar,
        Boolean possuiProblemaSaude,
        Set<ProgramaTransferencia> programasTransferencia,
        boolean vistoAssistenteSocial
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
                anexosFaltando,
                p.getDados().rendaFamiliar(),
                p.getDados().saude() != null ? p.getDados().saude().possuiProblema() : null,
                programasRecebidos(p.getDados()),
                p.isVistoAssistenteSocial());
    }

    private static Set<ProgramaTransferencia> programasRecebidos(PreCadastroDadosDTO dados) {
        PreCadastroDadosDTO.Transferencia transferencia = dados.transferencia();
        boolean recebe = transferencia != null && Boolean.TRUE.equals(transferencia.recebe()) && transferencia.programas() != null;
        return recebe ? transferencia.programas() : Set.of();
    }
}
