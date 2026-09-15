package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Oficina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OficinaRepository extends JpaRepository<Oficina, Long>, JpaSpecificationExecutor<Oficina> {

    /**
     * Usado pelo front para o alerta NAO BLOQUEANTE de duplicidade
     * (CA de FE): mesmo endpoint de listagem, filtrando por nome, e o
     * componente decide se mostra o aviso, sem impedir o submit.
     */
    boolean existsByNomeOficinaIgnoreCase(String nomeOficina);
}
