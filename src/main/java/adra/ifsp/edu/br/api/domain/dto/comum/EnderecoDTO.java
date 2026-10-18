package adra.ifsp.edu.br.api.domain.dto.comum;

import jakarta.validation.constraints.Size;

/** Endereço de assistido/responsável. Todos os campos são opcionais. */
public record EnderecoDTO(

        @Size(max = 180, message = "Rua deve ter no maximo 180 caracteres")
        String logradouro,

        @Size(max = 20, message = "Numero deve ter no maximo 20 caracteres")
        String numero,

        @Size(max = 120, message = "Bairro deve ter no maximo 120 caracteres")
        String bairro,

        @Size(max = 255, message = "Ponto de referencia deve ter no maximo 255 caracteres")
        String pontoReferencia
) {
}
