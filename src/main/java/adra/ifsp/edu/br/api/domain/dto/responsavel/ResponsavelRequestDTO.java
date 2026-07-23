package adra.ifsp.edu.br.api.domain.dto.responsavel;

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

        // CPF opcional e NAO unico, so validamos formato quando informado.
        @Pattern(regexp = "^$|\\d{11}", message = "CPF deve conter 11 digitos numericos")
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
