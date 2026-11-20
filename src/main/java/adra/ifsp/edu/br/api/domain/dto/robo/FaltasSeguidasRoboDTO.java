package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Robozinho ADRA — atendidos com {@code limiar}+ faltas seguidas nas chamadas
 * desde {@code desde}. Só nome, grupo e o número da regra; {@code total} conta
 * todos, {@code itens} vem cortado.
 */
public record FaltasSeguidasRoboDTO(int limiar, LocalDate desde, LocalDateTime geradoEm, List<Item> itens, long total) {

    public record Item(Long assistidoId, String nome, Long turmaId, String turma, long faltas, LocalDate ultimaChamada) {
    }
}
