package adra.ifsp.edu.br.api.domain.dto.matricula;

/**
 * Um dado da ficha ao lado do que a família enviou na rematrícula.
 * {@code chave}: qual parte da ficha a linha representa, para a tela poder
 * levar o valor enviado para a ficha (null nas linhas de responsável, que se
 * atualizam na aba Família).
 */
public record CampoComparadoDTO(String rotulo, String naFicha, String enviado, boolean diferente, String chave) {
}
