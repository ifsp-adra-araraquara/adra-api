package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.enums.StatusEntrega;
import adra.ifsp.edu.br.api.domain.model.NotificacaoEntrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificacaoEntregaRepository extends JpaRepository<NotificacaoEntrega, Long> {

    /** Pendentes do canal, com a notificação e o destinatário já carregados, na ordem em que foram criadas. */
    @Query("""
            SELECT e FROM NotificacaoEntrega e
            JOIN FETCH e.notificacao n
            JOIN FETCH n.destinatario
            WHERE e.canal = :canal AND e.status = adra.ifsp.edu.br.api.domain.enums.StatusEntrega.PENDENTE
            ORDER BY n.criadoEm, n.notificacaoId
            """)
    List<NotificacaoEntrega> pendentes(@Param("canal") CanalNotificacao canal);

    long countByCanalAndStatus(CanalNotificacao canal, StatusEntrega status);

    @Modifying
    @Transactional
    @Query("UPDATE NotificacaoEntrega e SET e.status = :status, e.enviadaEm = :quando WHERE e.entregaId IN :ids")
    int marcar(@Param("ids") Collection<Long> ids, @Param("status") StatusEntrega status, @Param("quando") OffsetDateTime quando);

    /**
     * Mensagens que saíram pelo canal desde {@code desde}. Um envio leva várias
     * notificações do mesmo destinatário com o mesmo horário, e conta como uma.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT n.destinatario_id, e.enviada_em
                  FROM adra.notificacao_entrega e
                  JOIN adra.notificacao n ON n.notificacao_id = e.notificacao_id
                 WHERE e.canal = :canal AND e.status = 'ENVIADA' AND e.enviada_em >= :desde
            ) envios
            """, nativeQuery = true)
    long mensagensDesde(@Param("canal") String canal, @Param("desde") OffsetDateTime desde);
}
