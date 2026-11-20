package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDate;

/**
 * Robozinho ADRA — um atendido numa lista do robô (busca por nome,
 * aniversariantes, um passo da próxima medida). Só o necessário para
 * identificar a pessoa e o grupo; nenhum dado sensível.
 *
 * @param data    aniversário (aniversariantes); null nas demais listas
 * @param detalhe texto curto da regra (ex.: "2 de 3 ocorrências"); pode ser null
 */
public record AtendidoRoboDTO(Long assistidoId, String nome, String grupo, LocalDate data, String detalhe) {
}
