package adra.ifsp.edu.br.api.domain.dto.arquivo;

import java.time.Instant;

public record UrlArquivoDTO(String url, Instant expiraEm) {
}