package adra.ifsp.edu.br.api.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
// Cache antes da transação: um acerto não pega conexão do pool.
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig {

    public static final String FOTOS_PERFIL = "fotos-perfil";
    public static final String URLS_ARQUIVO = "urls-arquivo";

    private static final int MAXIMO_POR_CACHE = 2_000;

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager cacheManager(@Value("${adra.arquivos.url-validade:5m}") Duration validadeUrl) {
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setAllowNullValues(false);
        caffeine.registerCustomCache(FOTOS_PERFIL, limitado(Duration.ofMinutes(10)));
        // Metade da validade: a URL entregue ainda dura pelo menos a outra metade.
        caffeine.registerCustomCache(URLS_ARQUIVO, limitado(validadeUrl.dividedBy(2)));
        // Limpa só após o commit: antes, outra request poderia guardar o dado antigo.
        return new TransactionAwareCacheManagerProxy(caffeine);
    }

    private static Cache<Object, Object> limitado(Duration validade) {
        return Caffeine.newBuilder()
                .maximumSize(MAXIMO_POR_CACHE)
                .expireAfterWrite(validade)
                .recordStats()
                .build();
    }
}
