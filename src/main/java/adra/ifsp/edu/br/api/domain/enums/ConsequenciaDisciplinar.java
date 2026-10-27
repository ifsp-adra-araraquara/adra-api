package adra.ifsp.edu.br.api.domain.enums;

/** O que um registro disparou ao completar 3 do mesmo nível — ver NivelDisciplinar. */
public enum ConsequenciaDisciplinar {
    /** Gerou uma advertência automaticamente (resolvida na hora). */
    ADVERTENCIA_GERADA(false),
    /** Gerou uma suspensão de 3 dias automaticamente (resolvida na hora). */
    SUSPENSAO_GERADA(false),
    /** Pendente até a coordenação registrar que o passeio foi perdido. */
    PERDE_PROXIMO_PASSEIO(true),
    /** Pendente até a coordenação registrar a conversa com o responsável. */
    RETORNO_COM_RESPONSAVEL(true);

    private final boolean exigeResolucao;

    ConsequenciaDisciplinar(boolean exigeResolucao) {
        this.exigeResolucao = exigeResolucao;
    }

    public boolean exigeResolucao() { return exigeResolucao; }
}
