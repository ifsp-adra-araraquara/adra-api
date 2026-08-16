package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndUsuarioIdNot(String email, Long usuarioId);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByAuthUid(UUID authUid);
}
