package adra.ifsp.edu.br.api.infra.armazenamento;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.exception.ArmazenamentoException;

@Component
public class SupabaseArmazenamento implements ArmazenamentoArquivos {

    private final RestClient rest;
    private final String storageUrl;
    private final String bucket;

    public SupabaseArmazenamento(@Value("${adra.supabase.url}") String url,
                                 @Value("${adra.supabase.service-key}") String serviceKey,
                                 @Value("${adra.arquivos.supabase.bucket}") String bucket) {
        this.storageUrl = url + "/storage/v1";
        this.bucket = bucket;

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(30));

        this.rest = RestClient.builder()
                .requestFactory(fabrica)
                .baseUrl(storageUrl)
                .defaultHeader("apikey", serviceKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                .build();
    }

    @Override
    public ProvedorArmazenamento provedor() {
        return ProvedorArmazenamento.SUPABASE;
    }

    @Override
    public void salvar(String chave, byte[] conteudo, String mimeType) {
        try {
            rest.post()
                    .uri("/object/" + bucket + "/" + chave)
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header("x-upsert", "false")
                    .body(conteudo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ArmazenamentoException("Falha ao enviar arquivo ao Supabase: " + chave, e);
        }
    }

    @Override
    public URI urlTemporaria(String chave, Duration validade) {
        try {
            Assinatura assinatura = rest.post()
                    .uri("/object/sign/" + bucket + "/" + chave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", validade.toSeconds()))
                    .retrieve()
                    .body(Assinatura.class);
            // signedURL vem relativo: /object/sign/<bucket>/<chave>?token=...
            return URI.create(storageUrl + assinatura.signedURL());
        } catch (RestClientException e) {
            throw new ArmazenamentoException("Falha ao gerar URL do arquivo: " + chave, e);
        }
    }

    @Override
    public void remover(String chave) {
        try {
            rest.method(HttpMethod.DELETE)
                    .uri("/object/" + bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", List.of(chave)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ArmazenamentoException("Falha ao remover arquivo do Supabase: " + chave, e);
        }
    }

    private record Assinatura(String signedURL) {
    }
}