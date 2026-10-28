package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreCadastroRepository extends JpaRepository<PreCadastro, Long> {
    List<PreCadastro> findByStatusOrderByCriadoEmDesc(StatusPreCadastro status);
}
