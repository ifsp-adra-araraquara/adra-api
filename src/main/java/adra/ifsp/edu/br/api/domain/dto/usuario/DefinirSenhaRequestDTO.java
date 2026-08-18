package adra.ifsp.edu.br.api.domain.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DefinirSenhaRequestDTO(

        @NotBlank(message = "E-mail e' obrigatorio")
        @Email(message = "E-mail invalido")
        String email,

        // Letra + digito quem valida e' o Supabase.
        @NotBlank(message = "Senha e' obrigatoria")
        @Size(min = 8, message = "Senha deve ter no minimo 8 caracteres")
        String novaSenha
) {
}
