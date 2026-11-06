package adra.ifsp.edu.br.api.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import adra.ifsp.edu.br.api.domain.service.TokenService;
import adra.ifsp.edu.br.api.domain.service.UsuarioService;

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
    public SecurityFilterChain chainApi(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                        UsuarioService usuarioService) throws Exception {
        return http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/pre-cadastro").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(decoderAplicacao(usuarioService))
                        .jwtAuthenticationConverter(jwtAuthenticationConverter))) 
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> autoridades = new ArrayList<>();
            String perfil = jwt.getClaimAsString(TokenService.CLAIM_PERFIL);
            if (perfil != null) {
                autoridades.add(new SimpleGrantedAuthority("ROLE_" + perfil));
            }
            if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(TokenService.CLAIM_ADMINISTRADOR))) {
                autoridades.add(new SimpleGrantedAuthority("ADMINISTRADOR"));
            }
            return autoridades;
        });
        return converter;
    }

    // O Supabase assina com ES256; o padrao do Nimbus e' RS256.
    @Bean
    public JwtDecoder decoderSupabase(@Value("${adra.supabase.jwks-uri}") String jwksUri) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 segundos
        factory.setReadTimeout(10000); // 10 segundos

        RestTemplate restTemplate = new RestTemplate(factory);

        return NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .restOperations(restTemplate)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${adra.cors.origens}") List<String> origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chaveSimetrica()));
    }

    private JwtDecoder decoderAplicacao(UsuarioService usuarioService) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveSimetrica())
                .macAlgorithm(ALGORITMO)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), usuarioAtivo(usuarioService)));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> usuarioAtivo(UsuarioService usuarioService) {
        return jwt -> usuarioService.estaAtivo(Long.valueOf(jwt.getSubject()))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Usuario inativo.", null));
    }

    private SecretKeySpec chaveSimetrica() {
        return new SecretKeySpec(segredo.getBytes(), ALGORITMO.getName());
    }
}
