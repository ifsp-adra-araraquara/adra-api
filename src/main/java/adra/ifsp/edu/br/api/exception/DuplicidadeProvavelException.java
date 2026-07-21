package adra.ifsp.edu.br.api.exception;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;

import java.util.List;

/**
 * Nao e' um erro tecnico - e' um alerta de negocio (card "Cadastrar assistido":
 * "Alerta de duplicidade provavel (mesmo nome + data de nascimento)").
 * O front deve mostrar os possiveis duplicados e perguntar se o usuario
 * quer confirmar o cadastro mesmo assim (reenviando com
 * confirmarApesarDeDuplicidade=true).
 */
public class DuplicidadeProvavelException extends RuntimeException {

    private final List<AssistidoResponseDTO> possiveisDuplicados;

    public DuplicidadeProvavelException(List<AssistidoResponseDTO> possiveisDuplicados) {
        super("Ja existe(m) assistido(s) cadastrado(s) com o mesmo nome e data de nascimento.");
        this.possiveisDuplicados = possiveisDuplicados;
    }

    public List<AssistidoResponseDTO> getPossiveisDuplicados() {
        return possiveisDuplicados;
    }
}
