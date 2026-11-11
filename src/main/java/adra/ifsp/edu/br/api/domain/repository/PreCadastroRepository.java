package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.model.PreCadastro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface PreCadastroRepository extends JpaRepository<PreCadastro, Long> {
    List<PreCadastro> findByStatusOrderByCriadoEmDesc(StatusPreCadastro status);

    List<PreCadastro> findByStatusOrderByCriadoEmAsc(StatusPreCadastro status);

    List<PreCadastro> findByStatusAndAnoReferenciaOrderByCriadoEmDesc(StatusPreCadastro status, Integer anoReferencia);

    List<PreCadastro> findByStatusAndAnoReferenciaOrderByCriadoEmAsc(StatusPreCadastro status, Integer anoReferencia);

    long countByStatus(StatusPreCadastro status);

    long countByCriadoEmGreaterThanEqual(OffsetDateTime inicio);

    @Modifying
    @Query("update PreCadastro p set p.vistoAssistenteSocial = true where p.preCadastroId = :id and p.vistoAssistenteSocial = false")
    void marcarVistoPelaAssistenteSocial(Long id);
}
