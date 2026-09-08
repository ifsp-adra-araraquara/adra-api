package adra.ifsp.edu.br.api.domain.dto.vinculo;


import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record VinculoFamiliarComResponsavelRequestDTO(

        @NotBlank
        String nomeCompleto,

        LocalDate dataNascimento,

        String cpf,

        String telefone,

        String email,

        String endereco,

        String observacoes,

        // ---- campos do vínculo (não do responsável) ----
        String parentesco,

        boolean responsavelPrincipal,

        boolean contatoEmergencia,

        boolean autorizadoRetirada

) {}