package adra.ifsp.edu.br.api.domain.enums;

/**
 * Nível do registro disciplinar (cartazes "Disciplina ADRA"). Cada motivo
 * pertence a um nível; ao completar 3 registros ativos do mesmo nível, o
 * DisciplinarService aplica a consequência do nível (ver {@link #consequencia()}).
 * UNIFORME é uma trilha à parte: não entra na escada ocorrência → advertência.
 */
public enum NivelDisciplinar {
    OCORRENCIA(false, ConsequenciaDisciplinar.ADVERTENCIA_GERADA),
    ADVERTENCIA(true, ConsequenciaDisciplinar.SUSPENSAO_GERADA),
    SUSPENSAO(true, ConsequenciaDisciplinar.PERDE_PROXIMO_PASSEIO),
    UNIFORME(true, ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL);

    /** A cada quantos registros ativos do nível a consequência é aplicada. */
    public static final int LIMITE_PROGRESSAO = 3;

    private final boolean geraDocumento;
    private final ConsequenciaDisciplinar consequencia;

    NivelDisciplinar(boolean geraDocumento, ConsequenciaDisciplinar consequencia) {
        this.geraDocumento = geraDocumento;
        this.consequencia = consequencia;
    }

    /** Advertência, suspensão e uniforme geram documento para os pais assinarem; ocorrência simples não. */
    public boolean geraDocumento() { return geraDocumento; }

    public ConsequenciaDisciplinar consequencia() { return consequencia; }
}
