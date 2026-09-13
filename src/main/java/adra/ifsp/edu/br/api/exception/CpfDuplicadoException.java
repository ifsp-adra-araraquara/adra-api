package adra.ifsp.edu.br.api.exception;

import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelResponseDTO;

/**
 * Erro de integridade de negocio (nao "provavel" como em Assistido) - CPF e' UNIQUE.
 * Nao ha opcao de "confirmar mesmo assim": o front deve oferecer reaproveitar
 * o registro existente ou cancelar a operacao.
 */
public class CpfDuplicadoException extends RuntimeException {

    private final ResponsavelResponseDTO responsavelExistente;

    public CpfDuplicadoException(ResponsavelResponseDTO responsavelExistente) {
        super("Ja existe um responsavel cadastrado com este CPF.");
        this.responsavelExistente = responsavelExistente;
    }

    public ResponsavelResponseDTO getResponsavelExistente() {
        return responsavelExistente;
    }
}