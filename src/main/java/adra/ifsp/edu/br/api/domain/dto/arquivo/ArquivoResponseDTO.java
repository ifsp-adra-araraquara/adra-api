package adra.ifsp.edu.br.api.domain.dto.arquivo;

import java.time.LocalDateTime;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.model.Arquivo;

/** Sem chave nem provedor: detalhe interno, o front pede a URL por /api/arquivos/{id}/url. */
public record ArquivoResponseDTO(
        Long arquivoId,
        EntidadeArquivo entidadeTipo,
        Long entidadeId,
        CategoriaArquivo categoria,
        String nomeOriginal,
        String mimeType,
        Long tamanhoBytes,
        LocalDateTime criadoEm
) {
    public static ArquivoResponseDTO de(Arquivo a) {
        return new ArquivoResponseDTO(a.getArquivoId(), a.getEntidadeTipo(), a.getEntidadeId(),
                a.getCategoria(), a.getNomeOriginal(), a.getMimeType(), a.getTamanhoBytes(), a.getCriadoEm());
    }
}