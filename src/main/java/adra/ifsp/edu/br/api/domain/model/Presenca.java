package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Uma linha por (aula, assistido) — CA-65.1 e CA-65.4. Guarda só o status;
 * os detalhes de falta justificada (motivo/observacao) ficam no satélite
 * FaltaJustificada, criado/removido pelo service junto com a troca de status
 * (não mapeado aqui como @OneToOne de propósito, pra manter esse ciclo de
 * vida explícito no lugar que faz a validação do CA-65.2).
 */
@Data
@NoArgsConstructor
@Entity
@Table(
        name = "presenca",
        schema = "adra",
        uniqueConstraints = @UniqueConstraint(name = "uq_presenca_aula_assistido", columnNames = {"aula_id", "assistido_id"})
)
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "presenca_id")
    private Long presencaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistido_id", nullable = false)
    private Assistido assistido;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_presenca", nullable = false, length = 20)
    private StatusPresenca statusPresenca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id", nullable = false, updatable = false)
    private Usuario criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_id", nullable = false)
    private Usuario atualizadoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}