package adra.ifsp.edu.br.api.domain.dto.guarda;

/** Arquivos no Storage sem dono no sistema (e a fila de remoção que ainda não saiu). */
public record OrfaosDTO(int arquivos, long bytes, long naFilaDeRemocao) {
}
