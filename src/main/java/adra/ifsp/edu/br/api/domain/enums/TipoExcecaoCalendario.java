package adra.ifsp.edu.br.api.domain.enums;

/** Motivo de um dia sem aula no calendário. */
public enum TipoExcecaoCalendario {
    FERIADO,
    RECESSO,
    FERIAS,
    PONTO_FACULTATIVO,
    /** Evento ou passeio no lugar da aula. */
    EVENTO_PASSEIO,
    OUTRO
}
