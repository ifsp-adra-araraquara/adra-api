package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Satélite de Presenca: só existe uma linha aqui quando
 * presenca.statusPresenca == FALTA_JUSTIFICADA (CA-65.2).
 */
@Data
@NoArgsConstructor
@Entity
@Table(
        name = "falta_justificada",
        schema = "adra",
        uniqueConstraints = @UniqueConstraint(name = "uq_falta_justificada_presenca", columnNames = "presenca_id")
)
public class FaltaJustificada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "falta_justificada_id")
    private Long faltaJustificadaId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presenca_id", nullable = false)
    private Presenca presenca;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_falta", nullable = false, length = 30)
    private MotivoFalta motivoFalta;

    @Column(name = "observacao", columnDefinition = "TEXT")
    private String observacao;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}