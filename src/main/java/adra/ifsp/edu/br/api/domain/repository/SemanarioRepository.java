package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Semanario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SemanarioRepository extends JpaRepository<Semanario, Long> {

    Optional<Semanario> findByOficina_OficinaIdAndTurma_TurmaIdAndSemanaInicio(Long oficinaId, Long turmaId, LocalDate semanaInicio);

    List<Semanario> findByOficina_OficinaIdOrderBySemanaInicioDesc(Long oficinaId);

    List<Semanario> findByOficina_OficineiroResponsavel_UsuarioIdOrderBySemanaInicioDesc(Long oficineiroId);

    /** O semanário é de uma oficina deste oficineiro? (ver AcessoTurmaService) */
    boolean existsBySemanarioIdAndOficina_OficineiroResponsavel_UsuarioId(Long semanarioId, Long oficineiroId);
}
