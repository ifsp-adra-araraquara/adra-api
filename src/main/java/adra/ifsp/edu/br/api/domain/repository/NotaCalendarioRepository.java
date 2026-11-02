package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.NotaCalendario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NotaCalendarioRepository extends JpaRepository<NotaCalendario, Long> {

    List<NotaCalendario> findAllByOrderByDataNotaAscHorarioAsc();

    List<NotaCalendario> findByDataNotaBetweenOrderByDataNotaAscHorarioAsc(LocalDate inicio, LocalDate fim);
}
