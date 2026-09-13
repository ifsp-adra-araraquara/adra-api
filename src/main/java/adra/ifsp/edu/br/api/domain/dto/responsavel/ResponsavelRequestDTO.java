package adra.ifsp.edu.br.api.domain.dto.responsavel;

import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

        String endereco,

        String observacoes
) {
}