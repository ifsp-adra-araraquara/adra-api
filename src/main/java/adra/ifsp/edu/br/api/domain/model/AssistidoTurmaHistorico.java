package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Um periodo em que o assistido esteve vinculado a uma turma.
 * data_fim nulo = vinculo aberto (turma atual do assistido).
 * Trocar de turma fecha o registro aberto e abre um novo - nunca
 * sobrescreve, para preservar o historico (CA-A04.2).
 */
@Entity
@Table(name = "assistido_turma_historico", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistidoTurmaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historico_id")
    private Long historicoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assistido_id")
    private Assistido assistido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @Column(name = "data_inicio", nullable = false)
    @Builder.Default
    private OffsetDateTime dataInicio = OffsetDateTime.now();

    @Column(name = "data_fim")
    private OffsetDateTime dataFim;

    @CreationTimestamp
    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
