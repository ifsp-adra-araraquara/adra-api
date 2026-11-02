package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Turma;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChamadaRepository extends JpaRepository<Chamada, Long> {

    @EntityGraph(attributePaths = {"criadoPor", "atualizadoPor"})
    Optional<Chamada> findByTurmaAndDataChamada(Turma turma, LocalDate dataChamada);

    @EntityGraph(attributePaths = {"turma", "criadoPor"})
    List<Chamada> findByDataChamada(LocalDate dataChamada);

    List<Chamada> findTop10ByTurmaAndDataChamadaLessThanOrderByDataChamadaDesc(Turma turma, LocalDate dataChamada);

    List<Chamada> findByTurmaIn(Collection<Turma> turmas);

    List<Chamada> findByTurmaAndDataChamadaBetween(Turma turma, LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"turma", "criadoPor"})
    List<Chamada> findByDataChamadaBetween(LocalDate inicio, LocalDate fim);
}
