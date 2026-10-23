package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.OficinaOficineiroHistorico;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OficinaOficineiroHistoricoRepository extends JpaRepository<OficinaOficineiroHistorico, Long> {

    Optional<OficinaOficineiroHistorico> findByOficina_OficinaIdAndDataFimIsNull(Long oficinaId);

    @EntityGraph(attributePaths = "oficineiro")
    List<OficinaOficineiroHistorico> findByOficina_OficinaIdOrderByDataInicioDescHistoricoIdDesc(Long oficinaId);
}
