package adra.ifsp.edu.br.api.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import adra.ifsp.edu.br.api.domain.enums.StatusSolicitacaoDesligamento;
import adra.ifsp.edu.br.api.domain.model.SolicitacaoDesligamento;

public interface SolicitacaoDesligamentoRepository extends JpaRepository<SolicitacaoDesligamento, Long> {

    boolean existsByAssistido_AssistidoIdAndStatus(Long assistidoId, StatusSolicitacaoDesligamento status);

    long countByStatus(StatusSolicitacaoDesligamento status);

    /** Pendentes e as decididas desde a data (para quem pediu ver a resposta), mais recentes primeiro. */
    @EntityGraph(attributePaths = {"assistido", "assistido.turma", "solicitadoPor", "decididoPor"})
    @Query("""
            select s from SolicitacaoDesligamento s
             where s.status = adra.ifsp.edu.br.api.domain.enums.StatusSolicitacaoDesligamento.PENDENTE
                or s.decididoEm >= :desde
             order by s.criadoEm desc
            """)
    List<SolicitacaoDesligamento> findPendentesERecentes(@Param("desde") OffsetDateTime desde);

    Optional<SolicitacaoDesligamento> findByAssistido_AssistidoIdAndStatus(Long assistidoId,
                                                                         StatusSolicitacaoDesligamento status);

    /** Encerramento do ano: quem não renovou foi desligado; o pedido em aberto fica cancelado. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE adra.solicitacao_desligamento s
               SET status = 'CANCELADA', resposta = :resposta, decidido_em = now()
              FROM adra.assistido a
             WHERE a.assistido_id = s.assistido_id AND a.status = 'INATIVO' AND s.status = 'PENDENTE'
            """, nativeQuery = true)
    int cancelarPendentesDeInativos(@Param("resposta") String resposta);
}
