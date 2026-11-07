package adra.ifsp.edu.br.api.domain.enums;

public enum StatusPreCadastro {
    PENDENTE,
    LISTA_ESPERA,
    CONVERTIDO,
    DESCARTADO;

    public boolean emAvaliacao() {
        return this == PENDENTE || this == LISTA_ESPERA;
    }
}
