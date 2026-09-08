package adra.ifsp.edu.br.api.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

@Service
public class BrevoEmailService {

    private final RestClient rest;
    private final Resource templateConvite;
    private final String remetenteEmail;
    private final String remetenteNome;
    private final String frontendUrl;

    public BrevoEmailService(
            @Value("${adra.brevo.api-key}") String apiKey,
            @Value("${adra.brevo.remetente-email}") String remetenteEmail,
            @Value("${adra.brevo.remetente-nome}") String remetenteNome,
            @Value("${adra.frontend.url}") String frontendUrl,
            @Value("classpath:templates/invite.html") Resource templateConvite) {
        this.rest = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        this.remetenteEmail = remetenteEmail;
        this.remetenteNome = remetenteNome;
        this.frontendUrl = frontendUrl;
        this.templateConvite = templateConvite;
    }

    public void enviarConvite(String email, String token) {
        String linkConvite = frontendUrl + "/convite/" + token;

        rest.post()
                .uri("/smtp/email")
                .body(Map.of(
                        "sender", Map.of("name", remetenteNome, "email", remetenteEmail),
                        "to", List.of(Map.of("email", email)),
                        "subject", "Convite — ADRA Araraquara",
                        "htmlContent", carregarHtml(linkConvite)
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private String carregarHtml(String linkConvite) {
        try {
            String template = StreamUtils.copyToString(templateConvite.getInputStream(), StandardCharsets.UTF_8);
            return template.replace("{{LINK_CONVITE}}", linkConvite);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel carregar o template de e-mail de convite.", e);
        }
    }
}