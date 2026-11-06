package adra.ifsp.edu.br.api.domain.enums;

/**
 * O que uma coluna (dia) da grade mensal representa — ver GradeMensalTurmaDTO.
 * Espelha a planilha que a instituição usava: dias com chamada e dias sem
 * atividade (feriado, recesso, passeio).
 */
public enum TipoColunaGrade {
    /** Chamada lançada. */
    LANCADA,
    /** Dia de chamada do grupo, hoje ou passado, ainda sem chamada. */
    PENDENTE,
    /** Dia de chamada futuro — coluna vazia, só pra grade do mês ficar completa. */
    FUTURA,
    /** Feriado nacional ou dia sem atividade no calendário. */
    SEM_CHAMADA
}
