package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.AssistidoTurmaHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssistidoTurmaHistoricoRepository extends JpaRepository<AssistidoTurmaHistorico, Long> {

    /** Vinculo de turma ainda aberto (data_fim nulo) do assistido, se houver. */
    Optional<AssistidoTurmaHistorico> findByAssistido_AssistidoIdAndDataFimIsNull(Long assistidoId);
}
