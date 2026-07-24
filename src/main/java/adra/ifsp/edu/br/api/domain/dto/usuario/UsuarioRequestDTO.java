package adra.ifsp.edu.br.api.domain.dto.usuario;

import adra.ifsp.edu.br.api.domain.enums.EspecialidadeSaude;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(

        @NotBlank(message = "Nome completo e' obrigatorio")
        @Size(max = 180, message = "Nome completo deve ter no maximo 180 caracteres")
        String nomeCompleto,

        @NotBlank(message = "E-mail e' obrigatorio")
        @Email(message = "E-mail invalido")
        @Size(max = 255)
        String email,

        @NotNull(message = "Nivel de permissao e' obrigatorio")
        NomeNivelPermissao nivelPermissao,

        // Obrigatorio somente quando nivelPermissao == PROFISSIONAL_SAUDE -
        // validado no service, nao aqui (a obrigatoriedade e' condicional
        // ao valor de outro campo, o que o Bean Validation padrao nao cobre bem).
        EspecialidadeSaude especialidade,

        @Size(max = 120)
        String cargoFuncao,

        @jakarta.validation.constraints.Pattern(regexp = "^$|\\d{10,11}", message = "Telefone deve conter DDD + numero (10 ou 11 digitos)")
        String telefone
) {
}
