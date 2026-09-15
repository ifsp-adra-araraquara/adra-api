package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AulaRepository extends JpaRepository<Aula, Long>, JpaSpecificationExecutor<Aula> {

    List<Aula> findByTurma(Turma turma);

    List<Aula> findByTurmaOrderByDataAulaAsc(Turma turma);

    Optional<Aula> findByTurmaAndDataAula(Turma turma, LocalDate dataAula);

    List<Aula> findByDataAula(LocalDate dataAula);

    List<Aula> findByTurmaAndStatusAula(Turma turma, StatusAula statusAula);

    boolean existsByTurmaAndDataAula(Turma turma, LocalDate dataAula);
}
