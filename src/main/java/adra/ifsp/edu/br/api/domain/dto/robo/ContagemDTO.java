package adra.ifsp.edu.br.api.domain.dto.robo;

/** Um rótulo e quantos atendidos (ou registros) caem nele. */
public record ContagemDTO(String rotulo, long total) {
}
