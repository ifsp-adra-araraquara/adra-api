package adra.ifsp.edu.br.api.web.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.auth.LoginResponseDTO;
import adra.ifsp.edu.br.api.domain.service.AutenticacaoService;
import lombok.RequiredArgsConstructor;

/**
 * Recebe o token do Supabase e devolve o token da propria aplicacao.
 * A validacao do token do Supabase acontece na cadeia de seguranca.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AutenticacaoService autenticacaoService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@AuthenticationPrincipal Jwt jwt) {
        UUID authUid = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(autenticacaoService.login(authUid));
    }
}
