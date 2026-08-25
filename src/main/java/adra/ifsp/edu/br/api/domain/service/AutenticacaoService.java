package adra.ifsp.edu.br.api.domain.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import adra.ifsp.edu.br.api.domain.dto.modulo.ModuloDTO;
import adra.ifsp.edu.br.api.domain.mapper.ModuloMapper;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoModuloRepository;
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
    private final NivelPermissaoModuloRepository nivelPermissaoModuloRepository;
    private final ModuloMapper moduloMapper;

    public LoginResponseDTO login(UUID authUid) {
        Usuario usuario = usuarioRepository.findByAuthUid(authUid)
                .orElseThrow(() -> new AutenticacaoException("Usuario nao cadastrado no sistema."));

        if (!usuario.isAtivo()) {
            throw new AutenticacaoException("Usuario inativo.");
        }

        usuario.setUltimoLogin(OffsetDateTime.now());
        usuarioRepository.save(usuario);

        List<ModuloDTO> modulos = nivelPermissaoModuloRepository
                .findComModuloById_NivelPermissaoIdOrderByOrdemAsc(usuario.getNivelPermissao().getNivelPermissaoId())
                .stream()
                .map(npm -> moduloMapper.paraDTO(npm.getModulo()))
                .toList();

        return new LoginResponseDTO(tokenService.gerarToken(usuario), usuarioMapper.paraDTO(usuario, modulos));
    }

   //public UsuarioResponseDTO autenticar(LoginRequestDTO dto) {
//        Usuario usuario = usuarioRepository.findByEmail(dto.email())
//                .filter(Usuario::isAtivo)
//                .filter(u -> passwordEncoder.matches(dto.senha(), u.getSenhaHash()))
//                .orElseThrow(() -> new CredenciaisInvalidasException("Email ou senha invalidos"));
//
//        usuario.setUltimoLogin(OffsetDateTime.now());
//        usuarioRepository.save(usuario);
//
//        List<ModuloDTO> modulos = nivelPermissaoModuloRepository
//                .findComModuloById_NivelPermissaoIdOrderByOrdemAsc(usuario.getNivelPermissao().getNivelPermissaoId())
//                .stream()
//                .map(npm -> moduloMapper.paraDTO(npm.getModulo()))
//                .toList();
//
//        return usuarioMapper.paraDTO(usuario, modulos);
//    }
}
