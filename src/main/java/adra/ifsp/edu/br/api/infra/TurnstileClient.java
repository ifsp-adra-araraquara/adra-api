package adra.ifsp.edu.br.api.infra;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TurnstileClient {

    private static final Logger log = LoggerFactory.getLogger(TurnstileClient.class);

    private final RestClient rest;
    private final String segredo;

    public TurnstileClient(@Value("${adra.turnstile.segredo}") String segredo) {
        this.segredo = segredo;

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(10));

        this.rest = RestClient.builder()
                .requestFactory(fabrica)
                .baseUrl("https://challenges.cloudflare.com/turnstile/v0")
                .build();
    }

    public boolean tokenValido(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            Verificacao verificacao = rest.post()
                    .uri("/siteverify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("secret", segredo, "response", token))
                    .retrieve()
                    .body(Verificacao.class);
            return verificacao != null && verificacao.success();
        } catch (RestClientException e) {
            log.warn("Nao foi possivel validar o captcha no Turnstile", e);
            return false;
        }
    }

    private record Verificacao(boolean success) {
    }
}
