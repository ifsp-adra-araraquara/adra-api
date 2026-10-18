package adra.ifsp.edu.br.api.domain.dto.vinculo;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.enums.Escolaridade;
import adra.ifsp.edu.br.api.domain.enums.EstadoCivil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record VinculoFamiliarResponseDTO(
        Long assistidoId,
        Long responsavelId,
        String nomeResponsavel,
        LocalDate dataNascimentoResponsavel,
        String cpfResponsavel,
        String telefoneResponsavel,
        String emailResponsavel,
        EnderecoDTO enderecoResponsavel,
        Escolaridade escolaridadeResponsavel,
        EstadoCivil estadoCivilResponsavel,
        String profissaoResponsavel,
        BigDecimal rendaResponsavel,
        String parentesco,
        boolean responsavelPrincipal,
        boolean contatoEmergencia,
        boolean autorizadoRetirada,
        String observacoes,
        OffsetDateTime criadoEm
) {
}
