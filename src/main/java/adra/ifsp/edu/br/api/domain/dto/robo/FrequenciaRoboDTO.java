package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Robozinho ADRA — frequência por (mês, grupo) entre {@code de} e {@code ate}
 * (yyyy-MM). O robô soma as linhas para responder "frequência do grupo",
 * "comparar grupos", "comparar meses" e "ranking das oficinas"; a regra é a
 * mesma do Painel (só P é presença; FJ é ausência).
 *
 * @param linhas só grupos com chamada lançada no mês, ordenados por mês e nome
 */
public record FrequenciaRoboDTO(String de, String ate, LocalDateTime geradoEm, List<Linha> linhas) {

    /**
     * @param oficinas    nomes das oficinas ativas do grupo, separados por ", " (null se nenhuma)
     * @param percentual  presença ÷ esperados, arredondado; null sem esperados
     */
    public record Linha(String mes, Long turmaId, String nomeTurma, String oficinas, long chamadas,
                        long esperados, long faltas, long justificadas, Integer percentual) {
    }
}
