package adra.ifsp.edu.br.api.domain.dto.vinculo;

import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record VinculoFamiliarComResponsavelRequestDTO(

        @NotBlank(message = "Nome completo e' obrigatorio")
        String nomeCompleto,

        LocalDate dataNascimento,

        @NotBlank(message = "CPF e' obrigatorio")
        @ValidCpf(message = "CPF invalido")
        String cpf,

        @Pattern(regexp = "^$|\\d{10,11}", message = "Telefone deve conter DDD + numero (10 ou 11 digitos)")
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