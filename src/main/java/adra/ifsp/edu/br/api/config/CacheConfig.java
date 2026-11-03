package adra.ifsp.edu.br.api.config;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
// Cache antes da transação: um acerto não pega conexão do pool.
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig implements CachingConfigurer {

    public static final String FOTOS_PERFIL = "fotos-perfil";
    public static final String URLS_ARQUIVO = "urls-arquivo";
    public static final String CALENDARIO = "calendario";
    public static final String REFERENCIAS = "referencias";
    public static final String ESTRUTURA = "estrutura";
    public static final String PAINEL = "painel";

    private static final int MAXIMO_POR_CACHE = 2_000;

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager cacheManager(@Value("${adra.arquivos.url-validade:5m}") Duration validadeUrl) {
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setAllowNullValues(false);
        caffeine.registerCustomCache(FOTOS_PERFIL, limitado(Duration.ofMinutes(10)));
        // Metade da validade: a URL entregue ainda dura pelo menos a outra metade.
        caffeine.registerCustomCache(URLS_ARQUIVO, limitado(validadeUrl.dividedBy(2)));
        caffeine.registerCustomCache(CALENDARIO, limitado(Duration.ofHours(1)));
        caffeine.registerCustomCache(REFERENCIAS, limitado(Duration.ofHours(1)));
        caffeine.registerCustomCache(ESTRUTURA, limitado(Duration.ofMinutes(30)));
        caffeine.registerCustomCache(PAINEL, limitado(Duration.ofMinutes(5)));
        // Limpa só após o commit: antes, outra request poderia guardar o dado antigo.
        return new TransactionAwareCacheManagerProxy(caffeine);
    }

    // Classe e método separam leituras no mesmo cache; o dia evita servir o que foi calculado ontem.
    @Override
    public KeyGenerator keyGenerator() {
        return (alvo, metodo, parametros) -> new SimpleKey(
                metodo.getDeclaringClass().getSimpleName(), metodo.getName(), LocalDate.now(), parametros);
    }

    private static Cache<Object, Object> limitado(Duration validade) {
        return Caffeine.newBuilder()
                .maximumSize(MAXIMO_POR_CACHE)
                .expireAfterWrite(validade)
                .recordStats()
                .build();
    }
}
