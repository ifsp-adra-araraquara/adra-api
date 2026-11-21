package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Robozinho ADRA — entradas e saídas de atendidos num ano.
 *
 * @param ativosInicio ativos em 1º de janeiro
 * @param ativosFim    ativos em 31 de dezembro (ou hoje, no ano corrente)
 * @param meses        os 12 meses, inclusive os sem movimento
 * @param motivos      motivos de saída mais comuns (texto livre normalizado)
 */
public record MovimentoAtendidosDTO(int ano, long ativosInicio, long ativosFim, long entradas, long saidas,
                                    LocalDateTime geradoEm, List<Mes> meses, List<ContagemDTO> motivos) {

    public record Mes(int mes, long entradas, long saidas) {
    }
}
