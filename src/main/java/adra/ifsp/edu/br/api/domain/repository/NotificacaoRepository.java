package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.model.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    boolean existsByTipoAndReferenciaAndDestinatario_UsuarioId(TipoAviso tipo, String referencia, Long destinatarioId);

    /** O que o usuário vê na plataforma: só as notificações com entrega nesse canal, mais novas primeiro. */
    @EntityGraph(attributePaths = "envolvidos")
    @Query(value = """
            SELECT n FROM Notificacao n
            WHERE n.destinatario.usuarioId = :usuarioId
              AND EXISTS (SELECT 1 FROM NotificacaoEntrega e WHERE e.notificacao = n AND e.canal = :canal)
            ORDER BY n.criadoEm DESC, n.notificacaoId DESC
            """,
            countQuery = """
            SELECT COUNT(n) FROM Notificacao n
            WHERE n.destinatario.usuarioId = :usuarioId
              AND EXISTS (SELECT 1 FROM NotificacaoEntrega e WHERE e.notificacao = n AND e.canal = :canal)
            """)
    Page<Notificacao> doDestinatario(@Param("usuarioId") Long usuarioId, @Param("canal") CanalNotificacao canal, Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notificacao n
            WHERE n.destinatario.usuarioId = :usuarioId AND n.lidaEm IS NULL
              AND EXISTS (SELECT 1 FROM NotificacaoEntrega e WHERE e.notificacao = n AND e.canal = :canal)
            """)
    long contarNaoLidas(@Param("usuarioId") Long usuarioId, @Param("canal") CanalNotificacao canal);

    Optional<Notificacao> findByNotificacaoIdAndDestinatario_UsuarioId(Long notificacaoId, Long destinatarioId);

    @Modifying
    @Query("UPDATE Notificacao n SET n.lidaEm = :agora WHERE n.destinatario.usuarioId = :usuarioId AND n.lidaEm IS NULL")
    int marcarTodasComoLidas(@Param("usuarioId") Long usuarioId, @Param("agora") OffsetDateTime agora);

    /* Retenção: apaga em ordem (filhos antes) para não depender do ON DELETE CASCADE. */

    @Modifying
    @Query(value = """
            DELETE FROM adra.notificacao_entrega
             WHERE notificacao_id IN (SELECT notificacao_id FROM adra.notificacao WHERE criado_em < :limite)
            """, nativeQuery = true)
    int apagarEntregasAntesDe(@Param("limite") OffsetDateTime limite);

    @Modifying
    @Query(value = """
            DELETE FROM adra.notificacao_envolvido
             WHERE notificacao_id IN (SELECT notificacao_id FROM adra.notificacao WHERE criado_em < :limite)
            """, nativeQuery = true)
    int apagarEnvolvidosAntesDe(@Param("limite") OffsetDateTime limite);

    @Modifying
    @Query(value = "DELETE FROM adra.notificacao WHERE criado_em < :limite", nativeQuery = true)
    int apagarAntesDe(@Param("limite") OffsetDateTime limite);
}
