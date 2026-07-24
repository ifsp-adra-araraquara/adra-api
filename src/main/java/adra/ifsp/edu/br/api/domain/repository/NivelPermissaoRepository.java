package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NivelPermissaoRepository extends JpaRepository<NivelPermissao, Long> {
    Optional<NivelPermissao> findByNome(NomeNivelPermissao nome);
}
