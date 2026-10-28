package adra.ifsp.edu.br.api.domain.dto.precadastro;

import jakarta.validation.constraints.NotBlank;
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

        LocalDate dataNascimento,

        @NotBlank(message = "Informe o nome do responsável.")
        @Size(max = 180)
        String responsavelNome,

        @Size(max = 20)
        String responsavelTelefone,

        @Size(max = 255)
        String endereco,

        String observacao,

        String site
) {
}
