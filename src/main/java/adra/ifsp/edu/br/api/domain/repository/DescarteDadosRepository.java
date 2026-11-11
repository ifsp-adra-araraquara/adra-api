package adra.ifsp.edu.br.api.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import adra.ifsp.edu.br.api.domain.model.DescarteDados;

public interface DescarteDadosRepository extends JpaRepository<DescarteDados, Long> {

    List<DescarteDados> findTop20ByOrderByExecutadoEmDesc();
}
