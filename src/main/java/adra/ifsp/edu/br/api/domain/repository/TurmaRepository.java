package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Turma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;

public interface TurmaRepository extends JpaRepository<Turma, Long>, JpaSpecificationExecutor<Turma> {
    List<Turma> findByAtivoTrue();

    @Query("SELECT t FROM Turma t WHERE t.ativo = true AND :diaSemana MEMBER OF t.diasDaSemana ORDER BY t.nomeTurma")
    List<Turma> findAtivasComChamadaNoDia(@Param("diaSemana") DayOfWeek diaSemana);
}