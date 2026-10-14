package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.util.List;

/**
 * Indicadores de frequência de um assistido, calculados a partir do status de
 * cada aula REALIZADA dos vínculos de turma dele (mesma reconstituição de
 * {@link PresencaService#buscarFrequenciaAssistido}: ausência de falta = presente).
 *
 * <p>Mesma regra da aba "Chamadas" da ficha no front: só PRESENTE conta como
 * presença; faltas seguidas consideram só FALTA (uma justificada interrompe a
 * sequência).
 *
 * @param percentual          % de presença, ou {@code null} se não houve aula realizada
 * @param totalAulas          aulas realizadas consideradas
 * @param faltasConsecutivas  faltas (não justificadas) seguidas mais recentes
 * @param emAcompanhamento    assistido ativo que precisa de atenção (ver {@link #de})
 */
public record IndicadorFrequencia(
        Integer percentual,
        int totalAulas,
        int faltasConsecutivas,
        boolean emAcompanhamento
) {

    /** Frequência mínima exigida (LDB, art. 24, VI). Abaixo disso: em acompanhamento. */
    public static final int FREQUENCIA_MINIMA = 75;

    /** Mesmo limiar do alerta de faltas seguidas da chamada (front: LIMIAR_FALTAS_CONSECUTIVAS). */
    public static final int LIMIAR_FALTAS_CONSECUTIVAS = 3;

    public static final IndicadorFrequencia SEM_AULAS = new IndicadorFrequencia(null, 0, 0, false);

    /**
     * @param statusMaisRecentePrimeiro status de cada aula realizada, da mais recente para a mais antiga
     * @param statusAssistido           só assistido ATIVO pode estar "em acompanhamento"
     */
    public static IndicadorFrequencia de(List<StatusPresenca> statusMaisRecentePrimeiro, StatusGeral statusAssistido) {
        int total = statusMaisRecentePrimeiro.size();
        if (total == 0) {
            return SEM_AULAS;
        }

        long presentes = statusMaisRecentePrimeiro.stream().filter(s -> s == StatusPresenca.PRESENTE).count();
        int percentual = (int) Math.round(presentes * 100.0 / total);

        int consecutivas = 0;
        for (StatusPresenca status : statusMaisRecentePrimeiro) {
            if (status != StatusPresenca.FALTA) {
                break;
            }
            consecutivas++;
        }

        boolean emAcompanhamento = statusAssistido == StatusGeral.ATIVO
                && (percentual < FREQUENCIA_MINIMA || consecutivas >= LIMIAR_FALTAS_CONSECUTIVAS);

        return new IndicadorFrequencia(percentual, total, consecutivas, emAcompanhamento);
    }
}
