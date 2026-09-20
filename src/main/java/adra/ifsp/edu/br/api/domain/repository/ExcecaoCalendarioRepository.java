package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.ExcecaoCalendario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExcecaoCalendarioRepository extends JpaRepository<ExcecaoCalendario, Long> {

    boolean existsByDataExcecao(LocalDate dataExcecao);

    Optional<ExcecaoCalendario> findByDataExcecao(LocalDate dataExcecao);

    List<ExcecaoCalendario> findByDataExcecaoBetween(LocalDate inicio, LocalDate fim);

    List<ExcecaoCalendario> findAllByOrderByDataExcecaoAsc();

    @Query("SELECT e.dataExcecao FROM ExcecaoCalendario e WHERE e.dataExcecao BETWEEN :inicio AND :fim")
    Set<LocalDate> findDatasExcecaoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
