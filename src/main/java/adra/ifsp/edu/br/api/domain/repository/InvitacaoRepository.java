package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.InvitacaoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvitacaoRepository extends JpaRepository<InvitacaoUsuario, Long> {
    Optional<InvitacaoUsuario> findByToken(String token);
}