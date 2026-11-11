package adra.ifsp.edu.br.api.domain.dto.matricula;

/** Um dado da ficha ao lado do que a família enviou na rematrícula. */
public record CampoComparadoDTO(String rotulo, String naFicha, String enviado, boolean diferente) {
}
