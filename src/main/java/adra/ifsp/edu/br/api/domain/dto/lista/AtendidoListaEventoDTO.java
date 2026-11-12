package adra.ifsp.edu.br.api.domain.dto.lista;

import java.time.LocalDate;

/**
 * Uma linha da lista de evento/passeio: só o necessário para conferir presença.
 * Responsável e telefone vêm só quando a coordenação pede (folha que sai da sede).
 */
public record AtendidoListaEventoDTO(
        Long assistidoId,
        String nomeCompleto,
        LocalDate dataNascimento,
        Long turmaId,
        String nomeTurma,
        String responsavelNome,
        String responsavelTelefone
) {
}
