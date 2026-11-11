package adra.ifsp.edu.br.api.infra.armazenamento;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.exception.ArmazenamentoException;
import adra.ifsp.edu.br.api.exception.ArquivoMuitoGrandeException;

@Component
public class SupabaseArmazenamento implements ArmazenamentoArquivos {

    private static final Logger log = LoggerFactory.getLogger(SupabaseArmazenamento.class);

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
        } catch (RestClientResponseException e) {
            if (recusadoPorTamanho(e)) {
                // Rede de segurança: a API já barra acima de CategoriaArquivo.Limite.BUCKET; se o limite do
                // bucket mudar no painel, o usuário recebe a mensagem certa em vez de "tente novamente".
                log.warn("Supabase recusou {} ({} bytes) por tamanho: {}", chave, conteudo.length,
                        e.getResponseBodyAsString());
                throw ArquivoMuitoGrandeException.acimaDe(CategoriaArquivo.Limite.BUCKET);
            }
            throw new ArmazenamentoException("Falha ao enviar arquivo ao Supabase: " + chave, e);
        } catch (RestClientException e) {
            throw new ArmazenamentoException("Falha ao enviar arquivo ao Supabase: " + chave, e);
        }
    }

    /**
     * O Storage responde 413 ou, em versões antigas, 400 com {"statusCode":"413","error":"Payload too large"}.
     */
    static boolean recusadoPorTamanho(RestClientResponseException e) {
        if (e.getStatusCode().value() == 413) {
            return true;
        }
        String corpo = e.getResponseBodyAsString();
        return corpo.contains("\"413\"") || corpo.contains("Payload too large")
                || corpo.contains("maximum allowed size");
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
    public Map<String, URI> urlsTemporarias(Collection<String> chaves, Duration validade) {
        try {
            AssinaturaEmLote[] assinaturas = rest.post()
                    .uri("/object/sign/" + bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", validade.toSeconds(), "paths", chaves))
                    .retrieve()
                    .body(AssinaturaEmLote[].class);
            return Arrays.stream(assinaturas)
                    .filter(assinatura -> assinatura.signedURL() != null)
                    .collect(Collectors.toMap(AssinaturaEmLote::path,
                            assinatura -> URI.create(storageUrl + assinatura.signedURL())));
        } catch (RestClientException e) {
            throw new ArmazenamentoException("Falha ao gerar URLs de arquivos", e);
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

    /** Pastas do bucket (tipo/categoria/arquivo) são percorridas em profundidade; 1000 itens por página. */
    @Override
    public List<ObjetoArmazenado> listarTodos() {
        List<ObjetoArmazenado> objetos = new ArrayList<>();
        listar("", objetos, 0);
        return objetos;
    }

    private static final int ITENS_POR_PAGINA = 1000;
    private static final int PROFUNDIDADE_MAXIMA = 5;

    private void listar(String prefixo, List<ObjetoArmazenado> objetos, int profundidade) {
        if (profundidade > PROFUNDIDADE_MAXIMA) {
            return;
        }
        for (int offset = 0; ; offset += ITENS_POR_PAGINA) {
            ItemDaLista[] itens;
            try {
                itens = rest.post()
                        .uri("/object/list/" + bucket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("prefix", prefixo, "limit", ITENS_POR_PAGINA, "offset", offset,
                                "sortBy", Map.of("column", "name", "order", "asc")))
                        .retrieve()
                        .body(ItemDaLista[].class);
            } catch (RestClientException e) {
                throw new ArmazenamentoException("Falha ao listar arquivos do Supabase em '" + prefixo + "'", e);
            }
            if (itens == null || itens.length == 0) {
                return;
            }
            for (ItemDaLista item : itens) {
                String caminho = prefixo.isEmpty() ? item.name() : prefixo + "/" + item.name();
                if (item.id() == null) {
                    listar(caminho, objetos, profundidade + 1); // pasta
                } else {
                    long tamanho = item.metadata() != null && item.metadata().get("size") instanceof Number n ? n.longValue() : 0;
                    objetos.add(new ObjetoArmazenado(caminho,
                            item.created_at() != null ? java.time.OffsetDateTime.parse(item.created_at()).toInstant() : Instant.EPOCH, tamanho));
                }
            }
            if (itens.length < ITENS_POR_PAGINA) {
                return;
            }
        }
    }

    private record ItemDaLista(String name, String id, String created_at, Map<String, Object> metadata) {
    }

    private record Assinatura(String signedURL) {
    }

    private record AssinaturaEmLote(String path, String signedURL) {
    }
}