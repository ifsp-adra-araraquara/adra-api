package adra.ifsp.edu.br.api.infra.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class SupabaseArmazenamentoTest {

    private static HttpClientErrorException erro(HttpStatus status, String corpo) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), null,
                corpo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("413 do Storage é recusa por tamanho")
    void status413() {
        assertThat(SupabaseArmazenamento.recusadoPorTamanho(erro(HttpStatus.CONTENT_TOO_LARGE, ""))).isTrue();
    }

    @Test
    @DisplayName("400 com statusCode 413 no corpo (formato do Storage) é recusa por tamanho")
    void status400ComCorpo413() {
        String corpo = "{\"statusCode\":\"413\",\"error\":\"Payload too large\","
                + "\"message\":\"The object exceeded the maximum allowed size\"}";
        assertThat(SupabaseArmazenamento.recusadoPorTamanho(erro(HttpStatus.BAD_REQUEST, corpo))).isTrue();
    }

    @Test
    @DisplayName("Outras recusas continuam como falha de armazenamento")
    void outraRecusa() {
        String corpo = "{\"statusCode\":\"403\",\"error\":\"Unauthorized\",\"message\":\"invalid signature\"}";
        assertThat(SupabaseArmazenamento.recusadoPorTamanho(erro(HttpStatus.BAD_REQUEST, corpo))).isFalse();
    }
}
