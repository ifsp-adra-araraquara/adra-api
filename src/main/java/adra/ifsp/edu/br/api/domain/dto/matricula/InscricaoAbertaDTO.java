package adra.ifsp.edu.br.api.domain.dto.matricula;

/**
 * Para o link público: de que ano é a inscrição feita hoje.
 * {@code periodoAberto} false = não há período aberto e a inscrição vale para o ano corrente.
 */
public record InscricaoAbertaDTO(int ano, boolean periodoAberto) {
}
