package adra.ifsp.edu.br.api.config;

import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Duas cadeias de seguranca porque sao dois emissores de token diferentes:
 * o login recebe o token do Supabase, o resto da API recebe o token que a
 * propria aplicacao emitiu no login.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROTA_LOGIN = "/api/auth/login";
    private static final MacAlgorithm ALGORITMO = MacAlgorithm.HS256;

    @Value("${adra.jwt.segredo}")
    private String segredo;

    @Bean
    @Order(1)
    public SecurityFilterChain chainLogin(HttpSecurity http, JwtDecoder decoderSupabase) throws Exception {
        return http
                .securityMatcher(ROTA_LOGIN)
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(decoderSupabase)))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain chainApi(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(decoderAplicacao())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter))) 
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String perfil = jwt.getClaimAsString(adra.ifsp.edu.br.api.domain.service.TokenService.CLAIM_PERFIL);
            if (perfil == null) {
                return List.<GrantedAuthority>of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + perfil));
        });
        return converter;
    }

    // O Supabase assina com ES256; o padrao do Nimbus e' RS256.
    @Bean
    public JwtDecoder decoderSupabase(@Value("${adra.supabase.jwks-uri}") String jwksUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${adra.cors.origens}") List<String> origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chaveSimetrica()));
    }

    private JwtDecoder decoderAplicacao() {
        return NimbusJwtDecoder.withSecretKey(chaveSimetrica())
                .macAlgorithm(ALGORITMO)
                .build();
    }

    private SecretKeySpec chaveSimetrica() {
        return new SecretKeySpec(segredo.getBytes(), ALGORITMO.getName());
    }
}
