package adra.ifsp.edu.br.api.domain.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import adra.ifsp.edu.br.api.domain.model.Usuario;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String CLAIM_PERFIL = "perfil";

    private final JwtEncoder jwtEncoder;

    @Value("${adra.jwt.expiracao}")
    private Duration expiracao;

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("adra-api")
                .subject(usuario.getUsuarioId().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(expiracao))
                .claim(CLAIM_PERFIL, usuario.getNivelPermissao().getNome().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
