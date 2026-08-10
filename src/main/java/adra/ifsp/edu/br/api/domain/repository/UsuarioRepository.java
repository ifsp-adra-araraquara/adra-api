package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmailIgnoreCaseAndUsuarioIdNot(String email, Long usuarioId);

    // Usado futuramente pelo CustomUserDetailsService (login/JWT).
    Optional<Usuario> findByEmailIgnoreCase(String email);
}
