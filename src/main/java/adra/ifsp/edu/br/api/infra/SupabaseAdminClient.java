package adra.ifsp.edu.br.api.infra;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SupabaseAdminClient {

    private final RestClient rest;

    public SupabaseAdminClient(@Value("${adra.supabase.url}") String url,
                               @Value("${adra.supabase.service-key}") String serviceKey) {
        this.rest = RestClient.builder()
                .baseUrl(url + "/auth/v1/admin")
                .defaultHeader("apikey", serviceKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                .build();
    }

    public UUID criarIdentidade(String email) {
        Identidade identidade = rest.post()
                .uri("/users")
                .body(Map.of("email", email, "email_confirm", true))
                .retrieve()
                .body(Identidade.class);

        return identidade.id();
    }

    public void definirSenha(UUID authUid, String senha) {
        rest.put()
                .uri("/users/{id}", authUid)
                .body(Map.of("password", senha))
                .retrieve()
                .toBodilessEntity();
    }

    public void removerIdentidade(UUID authUid) {
        rest.delete()
                .uri("/users/{id}", authUid)
                .retrieve()
                .toBodilessEntity();
    }

    private record Identidade(UUID id) {
    }
}
