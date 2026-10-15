package adra.ifsp.edu.br.api.infra;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class SupabaseAdminClient {

    private final RestClient rest;

    public SupabaseAdminClient(@Value("${adra.supabase.url}") String url,
                               @Value("${adra.supabase.service-key}") String serviceKey) {
        this.rest = RestClient.builder()
                .baseUrl(url + "/auth/v1")
                .defaultHeader("apikey", serviceKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                .build();
    }

    /** Cria a identidade e envia o convite; para quem ainda nao confirmou, so reenvia. */
    public UUID convidar(String email) {
        Identidade identidade = rest.post()
                .uri("/invite")
                .body(Map.of("email", email))
                .retrieve()
                .body(Identidade.class);

        return identidade.id();
    }

    /** Nao envia e-mail e invalida o link de redefinicao anterior. */
    public String gerarTokenRedefinicao(String email) {
        LinkGerado link = rest.post()
                .uri("/admin/generate_link")
                .body(Map.of("type", "recovery", "email", email))
                .retrieve()
                .body(LinkGerado.class);

        return link.hashedToken();
    }

    public void removerIdentidade(UUID authUid) {
        rest.delete()
                .uri("/admin/users/{id}", authUid)
                .retrieve()
                .toBodilessEntity();
    }

    public void alterarStatus(UUID authUid, boolean ativo) {
        String banDuration = ativo ? "none" : "876600h";
        rest.put()
                .uri("/admin/users/{id}", authUid)
                .body(Map.of("ban_duration", banDuration))
                .retrieve()
                .toBodilessEntity();
    }

    private record Identidade(UUID id) {
    }

    private record LinkGerado(@JsonProperty("hashed_token") String hashedToken) {
    }
}
