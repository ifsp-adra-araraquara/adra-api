package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmailIgnoreCaseAndUsuarioIdNot(String email, Long usuarioId);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByAuthUid(UUID authUid);

    boolean existsByUsuarioIdAndAtivoTrue(Long usuarioId);

    /** Destinatários das notificações de um perfil (FT-14). */
    List<Usuario> findByNivelPermissao_NomeAndAtivoTrue(NomeNivelPermissao nome);
}
