package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Modelo esparso: só faltas viram linha; sem linha na chamada = presente. */
public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    List<Presenca> findByChamada(Chamada chamada);

    /** Faltas de vários atendidos em várias chamadas numa query só (faltas seguidas, frequência, grade). */
    List<Presenca> findByChamadaInAndAssistidoIn(Collection<Chamada> chamadas, Collection<Assistido> assistidos);

    Optional<Presenca> findByChamadaAndAssistido(Chamada chamada, Assistido assistido);

    interface FrequenciaDoAssistido {
        Long getAssistidoId();

        int getTotal();

        int getPresentes();

        int getFaltasConsecutivas();
    }

    @Query(value = """
            WITH chamadas_do_assistido AS (
                SELECT DISTINCT ta.assistido_id, c.chamada_id, c.data_chamada
                  FROM adra.turma_aluno ta
                  JOIN adra.chamada c
                    ON c.turma_id = ta.turma_id
                   AND c.data_chamada >= ta.data_entrada
                   AND c.data_chamada <= COALESCE(ta.data_saida, :hoje)
                 WHERE ta.assistido_id IN (:assistidoIds)
            ),
            com_status AS (
                SELECT d.assistido_id, d.chamada_id, d.data_chamada,
                       COALESCE(CAST(p.status_presenca AS text), 'PRESENTE') AS status
                  FROM chamadas_do_assistido d
                  LEFT JOIN adra.presenca p
                    ON p.chamada_id = d.chamada_id AND p.assistido_id = d.assistido_id
            ),
            com_quebras AS (
                SELECT *, SUM(CASE WHEN status <> 'FALTA' THEN 1 ELSE 0 END)
                              OVER (PARTITION BY assistido_id ORDER BY data_chamada DESC, chamada_id DESC) AS quebras
                  FROM com_status
            )
            SELECT assistido_id AS "assistidoId",
                   COUNT(*) AS "total",
                   COUNT(*) FILTER (WHERE status = 'PRESENTE') AS "presentes",
                   COUNT(*) FILTER (WHERE quebras = 0) AS "faltasConsecutivas"
              FROM com_quebras
             GROUP BY assistido_id
            """, nativeQuery = true)
    List<FrequenciaDoAssistido> frequencias(@Param("assistidoIds") Collection<Long> assistidoIds, @Param("hoje") LocalDate hoje);
}
