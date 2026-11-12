package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.FaixaEtaria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaixaEtariaRepository extends JpaRepository<FaixaEtaria, Long> {

    List<FaixaEtaria> findAllByOrderByIdadeMinimaAscIdadeMaximaAsc();

    List<FaixaEtaria> findByAtivoTrueOrderByIdadeMinimaAscIdadeMaximaAsc();

    boolean existsByIdadeMinimaAndIdadeMaxima(Short idadeMinima, Short idadeMaxima);

    boolean existsByIdadeMinimaAndIdadeMaximaAndFaixaEtariaIdNot(Short idadeMinima, Short idadeMaxima, Long faixaEtariaId);
}
