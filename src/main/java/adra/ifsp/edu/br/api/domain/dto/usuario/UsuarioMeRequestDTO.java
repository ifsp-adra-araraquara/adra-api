package adra.ifsp.edu.br.api.domain.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioMeRequestDTO(

        @NotBlank(message = "Nome completo e' obrigatorio")
        @Size(max = 180, message = "Nome completo deve ter no maximo 180 caracteres")
        String nomeCompleto,

        @Pattern(regexp = "^$|\\d{10,11}", message = "Telefone deve conter DDD + numero (10 ou 11 digitos)")
        String telefone
) {
}

