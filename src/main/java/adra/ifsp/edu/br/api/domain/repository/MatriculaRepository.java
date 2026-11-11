package adra.ifsp.edu.br.api.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import adra.ifsp.edu.br.api.domain.model.Matricula;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    Optional<Matricula> findByAssistido_AssistidoIdAndAno(Long assistidoId, Integer ano);

    /** Histórico do atendido, do ano mais recente para o mais antigo. */
    List<Matricula> findByAssistido_AssistidoIdOrderByAnoDesc(Long assistidoId);
}
