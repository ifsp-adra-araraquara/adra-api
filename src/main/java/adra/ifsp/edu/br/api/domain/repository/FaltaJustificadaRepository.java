package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.FaltaJustificada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FaltaJustificadaRepository extends JpaRepository<FaltaJustificada, Long> {

    // Não precisa de um "registrarFaltaJustificada" próprio — o save() que já
    // vem de JpaRepository serve tanto pra criar quanto pra atualizar (ele decide
    // pelo id estar ou não preenchido). Um método customizado com esse nome não
    // ia nem compilar/subir: Spring Data só deriva query automática de nomes que
    // começam com findBy/existsBy/deleteBy/countBy etc., senão precisa de @Query.

    Optional<FaltaJustificada> findByPresenca_PresencaId(Long presencaId);

    List<FaltaJustificada> findByPresenca_PresencaIdIn(List<Long> presencaIds);

    void deleteByPresenca_PresencaId(Long presencaId);
}
