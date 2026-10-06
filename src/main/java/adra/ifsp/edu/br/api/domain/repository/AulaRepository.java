package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("SELECT COUNT(a) FROM Assistido a WHERE a.turma.turmaId = :turmaId AND a.status = 'ATIVO'")
    long countAlunosAtivosPorTurma(@Param("turmaId") Long turmaId);

    /**
     * Últimas (até) 10 aulas realizadas de uma turma antes de uma data —
     * usado pra calcular faltas consecutivas na hora de abrir a chamada
     * (ver PresencaService). Limite fixo pequeno: 10 aulas já cobre
     * qualquer limiar razoável de "faltas seguidas" sem trazer o histórico
     * inteiro da turma.
     */
    List<Aula> findTop10ByTurmaAndStatusAulaAndDataAulaLessThanOrderByDataAulaDesc(
            Turma turma, StatusAula statusAula, LocalDate dataAula);

    /** Mesma ideia, sem data-âncora — pra grade de frequência da turma (tela separada, não ligada a uma aula específica). */
    List<Aula> findTop10ByTurmaAndStatusAulaOrderByDataAulaDesc(Turma turma, StatusAula statusAula);

    /** Aulas realizadas de uma turma num período — usado pra montar o histórico de frequência de um assistido. */
    List<Aula> findByTurmaAndStatusAulaAndDataAulaBetweenOrderByDataAulaAsc(
            Turma turma, StatusAula statusAula, LocalDate dataInicio, LocalDate dataFim);
}
