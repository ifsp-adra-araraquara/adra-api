package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Modelo esparso: só faltas viram linha; sem linha na chamada = presente. */
public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    List<Presenca> findByChamada(Chamada chamada);

    /** Faltas de vários atendidos em várias chamadas numa query só (faltas seguidas, frequência, grade). */
    List<Presenca> findByChamadaInAndAssistidoIn(Collection<Chamada> chamadas, Collection<Assistido> assistidos);
}
