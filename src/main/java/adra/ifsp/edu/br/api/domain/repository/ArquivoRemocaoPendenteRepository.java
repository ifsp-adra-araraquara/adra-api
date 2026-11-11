package adra.ifsp.edu.br.api.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import adra.ifsp.edu.br.api.domain.model.ArquivoRemocaoPendente;

public interface ArquivoRemocaoPendenteRepository extends JpaRepository<ArquivoRemocaoPendente, String> {

    List<ArquivoRemocaoPendente> findTop200ByOrderByCriadoEmAsc();
}
