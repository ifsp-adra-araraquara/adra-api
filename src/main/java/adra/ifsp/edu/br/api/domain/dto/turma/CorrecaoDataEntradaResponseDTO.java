package adra.ifsp.edu.br.api.domain.dto.turma;

/**
 * Resultado da correção da entrada no grupo. `chamadasLancadas`: chamadas já feitas no
 * período corrigido, em que os corrigidos agora aparecem como presentes.
 */
public record CorrecaoDataEntradaResponseDTO(int corrigidos, long chamadasLancadas) {
}
