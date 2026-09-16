package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface TurmaRepository extends JpaRepository<Turma, Long>, JpaSpecificationExecutor<Turma> {
    List<Turma> findByOficineiroResponsavelUsuarioId(Long idOficineiro);
    List<Turma> findByOficinaOficinaId(Long idOficina);

    @Query("SELECT COUNT(t) > 0 FROM Turma t " +
           "WHERE t.oficineiroResponsavel.usuarioId = :oficineiroId " +
           "AND :diaSemana MEMBER OF t.diasDaSemana " +
           "AND t.ativo = true " +
           "AND ((t.horarioInicio <= :horarioFim AND t.horarioFim >= :horarioInicio))")
    boolean existsConflitoHorarioOficineiro(
            @Param("oficineiroId") Long oficineiroId,
            @Param("diaSemana") DayOfWeek diaSemana,
            @Param("horarioInicio") LocalTime horarioInicio,
            @Param("horarioFim") LocalTime horarioFim
    );
}