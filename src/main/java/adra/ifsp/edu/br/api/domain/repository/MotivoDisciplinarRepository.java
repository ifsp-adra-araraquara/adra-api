package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MotivoDisciplinarRepository extends JpaRepository<MotivoDisciplinar, Long> {

    List<MotivoDisciplinar> findAllByOrderByNivelAscNomeAsc();

    List<MotivoDisciplinar> findByAtivoTrueOrderByNivelAscNomeAsc();
}
