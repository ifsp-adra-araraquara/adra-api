package adra.ifsp.edu.br.api.domain.dto.comum;

import java.time.OffsetDateTime;
import java.util.List;

public record ErroRespostaDTO(
        OffsetDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<String> detalhes,
        String correlacao
) {
    public static ErroRespostaDTO de(int status, String erro, String mensagem, String caminho) {
        return new ErroRespostaDTO(OffsetDateTime.now(), status, erro, mensagem, caminho, null, null);
    }

    public static ErroRespostaDTO de(int status, String erro, String mensagem, String caminho, List<String> detalhes) {
        return new ErroRespostaDTO(OffsetDateTime.now(), status, erro, mensagem, caminho, detalhes, null);
    }

    /** O mesmo codigo vai pro log, para casar c/ erro do usuario e o trace. */
    public static ErroRespostaDTO interno(String mensagem, String caminho, String correlacao) {
        return new ErroRespostaDTO(OffsetDateTime.now(), 500, "Erro interno", mensagem, caminho, null, correlacao);
    }
}
