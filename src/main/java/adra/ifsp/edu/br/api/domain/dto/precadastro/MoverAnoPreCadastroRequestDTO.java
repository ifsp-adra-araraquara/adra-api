package adra.ifsp.edu.br.api.domain.dto.precadastro;

import jakarta.validation.constraints.NotNull;

/** "Matrícula do próximo ano": para qual ano a inscrição vai (ou volta). */
public record MoverAnoPreCadastroRequestDTO(@NotNull(message = "Informe o ano.") Integer ano) {
}
