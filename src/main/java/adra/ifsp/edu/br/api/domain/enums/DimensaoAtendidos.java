package adra.ifsp.edu.br.api.domain.enums;

/**
 * Robozinho ADRA — por onde agrupar os atendidos ativos em "quantos atendidos
 * por …". A expressão SQL fica aqui (constante), nunca vem do usuário.
 */
public enum DimensaoAtendidos {

    /** Faixa do grupo atual (mesma regra do Painel); calculada pelo PainelRepository. */
    FAIXA_ETARIA(null, ""),
    IDADE("date_part('year', age(CAST(:hoje AS date), s.data_nascimento))::int::text", ""),
    SEXO("COALESCE(s.sexo, 'NAO_INFORMADO')", ""),
    GRUPO("COALESCE(t.nome_turma, 'Sem grupo')", "LEFT JOIN adra.turma t ON t.turma_id = s.turma_id"),
    OFICINA("COALESCE(o.nome_oficina, 'Sem oficina')",
            "LEFT JOIN adra.oficina_turma ot ON ot.turma_id = s.turma_id AND ot.ativo"
                    + " LEFT JOIN adra.oficina o ON o.oficina_id = ot.oficina_id"),
    BAIRRO("COALESCE(NULLIF(initcap(btrim(s.endereco_bairro)), ''), 'Não informado')", ""),
    TURNO_ESCOLA("COALESCE(s.escola_turno, 'NAO_INFORMADO')", "");

    private final String expressao;
    private final String juncao;

    DimensaoAtendidos(String expressao, String juncao) {
        this.expressao = expressao;
        this.juncao = juncao;
    }

    public String expressao() {
        return expressao;
    }

    public String juncao() {
        return juncao;
    }
}
