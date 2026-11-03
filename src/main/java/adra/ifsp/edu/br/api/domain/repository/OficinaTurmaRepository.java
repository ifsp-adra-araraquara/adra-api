package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OficinaTurmaRepository extends JpaRepository<OficinaTurma, Long> {

    List<OficinaTurma> findByOficina_OficinaId(Long oficinaId);

    List<OficinaTurma> findByTurma_TurmaId(Long turmaId);

    Optional<OficinaTurma> findByOficina_OficinaIdAndTurma_TurmaIdAndAtivoTrue(Long oficinaId, Long turmaId);

    List<OficinaTurma> findByOficina_OficinaIdAndAtivoTrue(Long oficinaId);

    List<OficinaTurma> findByOficina_OficinaIdInAndAtivoTrue(Collection<Long> oficinaIds);

    List<OficinaTurma> findByTurma_TurmaIdInAndAtivoTrue(Collection<Long> turmaIds);

    List<OficinaTurma> findByOficina_OficineiroResponsavel_UsuarioIdAndAtivoTrue(Long oficineiroId);

    /** O oficineiro tem alguma oficina que atende este grupo? (ver AcessoTurmaService) */
    boolean existsByTurma_TurmaIdAndOficina_OficineiroResponsavel_UsuarioIdAndAtivoTrue(Long turmaId, Long oficineiroId);
}
