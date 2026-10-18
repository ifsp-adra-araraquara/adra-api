package adra.ifsp.edu.br.api.domain.dto.presenca;

/** Pessoa autorizada a buscar o aluno — aparece no cartão da chamada. */
public record AutorizadoRetiradaDTO(
        String nome,
        String parentesco,
        String telefone
) {
}
