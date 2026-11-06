package adra.ifsp.edu.br.api.domain.enums;

/**
 * Situação da chamada de um grupo num dia. Não é coluna no banco: é derivada
 * da existência da linha em `chamada`.
 */
public enum StatusChamada {
    LANCADA,
    PENDENTE
}
