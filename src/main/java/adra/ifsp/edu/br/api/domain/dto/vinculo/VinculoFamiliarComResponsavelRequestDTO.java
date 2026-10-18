package adra.ifsp.edu.br.api.domain.dto.vinculo;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.Escolaridade;
import adra.ifsp.edu.br.api.domain.enums.EstadoCivil;
import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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

        @Valid
        EnderecoDTO endereco,

        String observacoes,

        Escolaridade escolaridade,

        EstadoCivil estadoCivil,

        @Size(max = 120, message = "Profissao deve ter no maximo 120 caracteres")
        String profissao,

        @PositiveOrZero(message = "Renda nao pode ser negativa")
        @Digits(integer = 10, fraction = 2, message = "Renda invalida")
        BigDecimal renda,

        // ---- campos do vínculo (não do responsável) ----
        String parentesco,

        boolean responsavelPrincipal,

        boolean contatoEmergencia,

        boolean autorizadoRetirada

) {

    /** Só a parte "cadastro do responsável" deste payload. */
    public ResponsavelRequestDTO paraResponsavelRequest() {
        return new ResponsavelRequestDTO(
                nomeCompleto,
                dataNascimento,
                cpf,
                telefone,
                email,
                endereco,
                observacoes,
                escolaridade,
                estadoCivil,
                profissao,
                renda
        );
    }
}
