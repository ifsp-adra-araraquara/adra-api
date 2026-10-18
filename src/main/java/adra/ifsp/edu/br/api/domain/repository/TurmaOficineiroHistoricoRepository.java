package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.TurmaOficineiroHistorico;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurmaOficineiroHistoricoRepository extends JpaRepository<TurmaOficineiroHistorico, Long> {

    Optional<TurmaOficineiroHistorico> findByTurma_TurmaIdAndDataFimIsNull(Long turmaId);

    @EntityGraph(attributePaths = "oficineiro")
    List<TurmaOficineiroHistorico> findByTurma_TurmaIdOrderByDataInicioDescHistoricoIdDesc(Long turmaId);
}
