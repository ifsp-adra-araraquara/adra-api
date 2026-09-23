package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.AutenticacaoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * O token da API (chainApi no SecurityConfig, gerado por TokenService.gerarToken)
 * grava usuarioId como subject do JWT — é isso que dá pra usar aqui pra descobrir
 * quem está autenticado na requisição.
 */
@Service
@RequiredArgsConstructor
public class UsuarioAutenticadoServiceImpl implements UsuarioAutenticadoService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public Usuario getUsuarioAtual() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof Jwt jwt)) {
            throw new AutenticacaoException("Usuário não autenticado.");
        }

        Long usuarioId = Long.valueOf(jwt.getSubject());

        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AutenticacaoException("Usuário autenticado não encontrado: id " + usuarioId));
    }
}
