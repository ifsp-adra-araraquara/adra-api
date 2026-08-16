package adra.ifsp.edu.br.api.domain.dto.auth;

import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;

public record LoginResponseDTO(String token, UsuarioResponseDTO usuario) {
}
