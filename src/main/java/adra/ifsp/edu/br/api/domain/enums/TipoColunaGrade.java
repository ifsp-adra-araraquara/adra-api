package adra.ifsp.edu.br.api.domain.enums;

/**
 * O que uma coluna (dia) da grade mensal representa — ver GradeMensalTurmaDTO.
 * Espelha a planilha que a instituição usava: dias com chamada, dias de
 * passeio/evento (aula cancelada) e feriado/recesso (exceção de calendário).
 */
public enum TipoColunaGrade {
    /** Aula REALIZADA — tem chamada lançada. */
    AULA,
    /** Aula de hoje ou passada ainda sem chamada (PLANEJADA/REMARCADA). */
    PENDENTE,
    /** Aula futura — coluna vazia, só pra grade do mês ficar completa. */
    FUTURA,
    /** Aula CANCELADA (passeio, evento...) — rótulo vem das observações da aula. */
    CANCELADA,
    /** Feriado/recesso cadastrado no calendário, sem aula gerada. */
    SEM_AULA
}
