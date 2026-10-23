package adra.ifsp.edu.br.api.domain.dto.responsavel;

import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.enums.Escolaridade;
import adra.ifsp.edu.br.api.domain.enums.EstadoCivil;
import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResponsavelRequestDTO(

        @NotBlank(message = "Nome completo e' obrigatorio")
        @Size(max = 180, message = "Nome completo deve ter no maximo 180 caracteres")
        String nomeCompleto,

        @Past(message = "Data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        @NotBlank(message = "CPF e' obrigatorio")
        @ValidCpf(message = "CPF invalido")
        String cpf,

        @Pattern(regexp = "^$|\\d{10,11}", message = "Telefone deve conter DDD + numero (10 ou 11 digitos)")
        String telefone,

        @Email(message = "E-mail invalido")
        @Size(max = 255)
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
        BigDecimal renda
) {
}
