package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    List<Presenca> findByAula(Aula aula);

    /** US-68: busca presenças da aula com JOIN FETCH dos usuários (criadoPor e atualizadoPor) para evitar N+1 queries. */
    @Query("SELECT p FROM Presenca p LEFT JOIN FETCH p.criadoPor LEFT JOIN FETCH p.atualizadoPor WHERE p.aula = :aula")
    List<Presenca> findByAulaWithUsuarios(@Param("aula") Aula aula);

    List<Presenca> findByAssistido(Assistido assistido);

    /** US-68: busca presenças do assistido com JOIN FETCH dos usuários. */
    @Query("SELECT p FROM Presenca p LEFT JOIN FETCH p.criadoPor LEFT JOIN FETCH p.atualizadoPor WHERE p.assistido = :assistido")
    List<Presenca> findByAssistidoWithUsuarios(@Param("assistido") Assistido assistido);

    Optional<Presenca> findByAulaAndAssistido(Aula aula, Assistido assistido);

    /** US-71: quais destas aulas já têm ao menos uma presença lançada. */
    @Query("SELECT DISTINCT p.aula.aulaId FROM Presenca p WHERE p.aula.aulaId IN :aulaIds")
    Set<Long> findAulaIdsComPresenca(@Param("aulaIds") Collection<Long> aulaIds);
}
