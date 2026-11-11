package adra.ifsp.edu.br.api.domain.enums;

/** Situação da matrícula de um ano letivo. */
public enum StatusMatricula {
    /** Ano em curso: o atendido participa dos grupos. */
    ATIVA,
    /** Ano encerrado pelo Coordenador; só histórico. */
    ENCERRADA,
    /** Saiu da ADRA durante o ano. */
    DESLIGADA
}
