package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.CanalNotificacao;
import adra.ifsp.edu.br.api.domain.enums.StatusEntrega;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** FT-14: a entrega de uma notificação em um canal (uma por canal). */
@Entity
@Table(name = "notificacao_entrega", schema = "adra",
        uniqueConstraints = @UniqueConstraint(columnNames = {"notificacao_id", "canal"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entrega_id")
    private Long entregaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notificacao_id")
    private Notificacao notificacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", length = 20, nullable = false)
    private CanalNotificacao canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusEntrega status = StatusEntrega.PENDENTE;

    @Column(name = "enviada_em")
    private OffsetDateTime enviadaEm;

    public NotificacaoEntrega(Notificacao notificacao, CanalNotificacao canal) {
        this.notificacao = notificacao;
        this.canal = canal;
    }
}
