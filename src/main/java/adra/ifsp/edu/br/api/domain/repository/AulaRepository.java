package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.springframework.data.jpa.repository.Modifying;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AulaRepository extends JpaRepository<Aula, Long>, JpaSpecificationExecutor<Aula> {

    List<Aula> findByTurma(Turma turma);

    List<Aula> findByTurmaOrderByDataAulaAsc(Turma turma);

    List<Aula> findByTurmaOrderByDataAulaDescHorarioInicioDesc(Turma turma);

    Optional<Aula> findByTurmaAndDataAula(Turma turma, LocalDate dataAula);

    List<Aula> findByDataAula(LocalDate dataAula);

    List<Aula> findByTurmaAndStatusAula(Turma turma, StatusAula statusAula);

    boolean existsByTurmaAndDataAula(Turma turma, LocalDate dataAula);

    /** A aula é de uma turma em que este usuário é o oficineiro responsável? (ver AcessoTurmaService) */
    boolean existsByAulaIdAndTurmaOficineiroResponsavelUsuarioId(Long aulaId, Long usuarioId);

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

    /** Aulas de várias turmas num status, numa query só — indicadores de frequência da listagem de assistidos. */
    List<Aula> findByTurmaInAndStatusAula(Collection<Turma> turmas, StatusAula statusAula);

    /** Total de aulas da turma que não foram canceladas (realizadas + planejadas + remarcadas). */
    long countByTurmaAndStatusAulaNot(Turma turma, StatusAula statusAula);

    List<Aula> findByDataAulaBetweenAndStatusAula(LocalDate inicio, LocalDate fim, StatusAula statusAula);

    /**
     * Troca de oficineiro da turma: as aulas ainda não dadas (planejadas ou
     * remarcadas) a partir de `aPartirDe` passam para o novo. Realizadas e
     * passadas ficam com quem deu a aula.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            UPDATE Aula a SET a.oficineiro = :oficineiro
             WHERE a.turma = :turma
               AND a.dataAula >= :aPartirDe
               AND a.statusAula IN (adra.ifsp.edu.br.api.domain.enums.StatusAula.PLANEJADA,
                                    adra.ifsp.edu.br.api.domain.enums.StatusAula.REMARCADA)
            """)
    int reatribuirOficineiroAulasFuturas(@Param("turma") Turma turma,
                                        @Param("oficineiro") Usuario oficineiro,
                                        @Param("aPartirDe") LocalDate aPartirDe);
}
