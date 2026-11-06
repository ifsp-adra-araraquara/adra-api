package adra.ifsp.edu.br.api.domain.dto.precadastro;

import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.validation.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Preenchido pela família no link público, sem login. {@code site} é um
 * honeypot: campo que só um robô preenche (escondido via CSS no front) —
 * se vier com valor, o envio é descartado silenciosamente.
 */
public record PreCadastroRequestDTO(
        @NotBlank(message = "Informe o nome completo.")
        @Size(max = 180)
        String nomeCompleto,

        @NotNull(message = "Informe a data de nascimento.")
        @Past(message = "Data de nascimento deve estar no passado.")
        LocalDate dataNascimento,

        @NotBlank(message = "Informe o CPF do atendido.")
        @ValidCpf(message = "CPF do atendido inválido.")
        String cpf,

        @NotNull(message = "Informe o sexo do atendido.")
        Sexo sexo,

        @Email(message = "E-mail inválido.")
        @Size(max = 180)
        String email,

        @NotNull(message = "Preencha o formulário.")
        @Valid
        PreCadastroDadosDTO dados,

        String site
) {
}
