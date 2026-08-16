package adra.ifsp.edu.br.api.domain.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.dto.auth.LoginResponseDTO;
import adra.ifsp.edu.br.api.domain.mapper.UsuarioMapper;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.AutenticacaoException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final TokenService tokenService;

    public LoginResponseDTO login(UUID authUid) {
        Usuario usuario = usuarioRepository.findByAuthUid(authUid)
                .orElseThrow(() -> new AutenticacaoException("Usuario nao cadastrado no sistema."));

        if (!usuario.isAtivo()) {
            throw new AutenticacaoException("Usuario inativo.");
        }

        usuario.setUltimoLogin(OffsetDateTime.now());

        return new LoginResponseDTO(tokenService.gerarToken(usuario), usuarioMapper.paraDTO(usuario));
    }
}
