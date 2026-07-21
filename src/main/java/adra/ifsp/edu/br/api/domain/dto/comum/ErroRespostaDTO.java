package adra.ifsp.edu.br.api.domain.dto.comum;

import java.time.OffsetDateTime;
import java.util.List;

public record ErroRespostaDTO(
        OffsetDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<String> detalhes
) {
    public static ErroRespostaDTO de(int status, String erro, String mensagem, String caminho) {
        return new ErroRespostaDTO(OffsetDateTime.now(), status, erro, mensagem, caminho, null);
    }

    public static ErroRespostaDTO de(int status, String erro, String mensagem, String caminho, List<String> detalhes) {
        return new ErroRespostaDTO(OffsetDateTime.now(), status, erro, mensagem, caminho, detalhes);
    }
}
