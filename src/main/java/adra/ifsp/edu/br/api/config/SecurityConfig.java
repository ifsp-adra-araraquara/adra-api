package adra.ifsp.edu.br.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ATENCAO - TEMPORARIO: sem RBAC ainda. Combinado no card de organizacao do
 * projeto: entregar os CRUDs base primeiro, JWT + middleware de papeis entra
 * depois (era pre-requisito critico das outras features, mas decidimos
 * destravar o cadastro basico primeiro).
 *
 * O starter-security ja esta no classpath (build.gradle), entao sem este
 * bean o Spring Boot ligaria a autenticacao basica automatica e bloquearia
 * TODOS os endpoints com 401. Este config libera tudo por enquanto.
 *
 * Quando o JWT entrar, trocar por:
 *  - authorizeHttpRequests com regras por role (hasRole(...))
 *  - addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
 * A sessionCreationPolicy STATELESS ja fica correta desde ja.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
