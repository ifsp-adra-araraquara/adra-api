package adra.ifsp.edu.br.api.domain.dto.disciplinar;

/**
 * Marca o documento como devolvido assinado. `arquivoId` é opcional: a foto
 * ou PDF do documento assinado, já enviada por POST /api/arquivos (dono
 * ASSISTIDO, categoria DOCUMENTO_DISCIPLINAR_ASSINADO).
 */
public record DocumentoDevolvidoRequestDTO(Long arquivoId) {
}
