package adra.ifsp.edu.br.api.domain.dto.assistido;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AssistidoRequestDTO(

        @NotBlank(message = "Nome completo e' obrigatorio")
        @Size(max = 180, message = "Nome completo deve ter no maximo 180 caracteres")
        String nomeCompleto,

        @NotNull(message = "Data de nascimento e' obrigatoria")
        @Past(message = "Data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        // CPF opcional e NAO unico (definido explicitamente no card) - so
        // validamos o formato quando informado.
        @Pattern(regexp = "^$|\\d{11}", message = "CPF deve conter 11 digitos numericos")
        String cpf,

        // Se nao informado, o service assume LocalDate.now().
        LocalDate dataEntrada,

        String necessidadesEspecificas,

        String observacoes,

        // Flag de confirmacao do alerta de duplicidade provavel (mesmo nome +
        // nascimento). Default false: primeira tentativa sempre verifica.
        boolean confirmarApesarDeDuplicidade
) {
}
