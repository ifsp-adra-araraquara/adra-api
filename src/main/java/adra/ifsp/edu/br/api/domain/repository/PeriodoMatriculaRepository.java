package adra.ifsp.edu.br.api.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import adra.ifsp.edu.br.api.domain.model.PeriodoMatricula;

public interface PeriodoMatriculaRepository extends JpaRepository<PeriodoMatricula, Integer> {

    List<PeriodoMatricula> findAllByOrderByAnoDesc();

    /** Período aberto no dia; havendo mais de um, o do ano mais adiantado. */
    @Query("""
            select p from PeriodoMatricula p
            where p.inicio <= :dia and (p.fim is null or p.fim >= :dia)
            order by p.ano desc
            limit 1
            """)
    Optional<PeriodoMatricula> findAbertoEm(@Param("dia") LocalDate dia);
}
