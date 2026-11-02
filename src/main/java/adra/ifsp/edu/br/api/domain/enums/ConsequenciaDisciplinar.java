package adra.ifsp.edu.br.api.domain.enums;

/**
 * O que um registro recomenda ao completar 3 do mesmo nível — ver NivelDisciplinar.
 * Fica pendente até o Coordenador gerar o próximo registro ou dispensar.
 */
public enum ConsequenciaDisciplinar {
    /** Recomenda advertência escrita (3ª ocorrência). */
    RECOMENDA_ADVERTENCIA(true),
    /** Recomenda suspensão de 3 dias (3ª advertência). */
    RECOMENDA_SUSPENSAO(true),
    /** Pendente até a coordenação registrar que o passeio foi perdido. */
    PERDE_PROXIMO_PASSEIO(true),
    /** Pendente até a coordenação registrar a conversa com o responsável. */
    RETORNO_COM_RESPONSAVEL(true);

    private final boolean exigeResolucao;

    ConsequenciaDisciplinar(boolean exigeResolucao) {
        this.exigeResolucao = exigeResolucao;
    }

    public boolean exigeResolucao() { return exigeResolucao; }

    /** Recomendações que o Coordenador resolve gerando um registro do próximo nível. */
    public NivelDisciplinar nivelRecomendado() {
        return switch (this) {
            case RECOMENDA_ADVERTENCIA -> NivelDisciplinar.ADVERTENCIA;
            case RECOMENDA_SUSPENSAO -> NivelDisciplinar.SUSPENSAO;
            default -> null;
        };
    }
}
