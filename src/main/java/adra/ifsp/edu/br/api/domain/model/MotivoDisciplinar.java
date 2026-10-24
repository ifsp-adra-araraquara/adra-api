package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** Motivo de registro disciplinar, cadastrável pela coordenação. O nível do registro vem daqui. */
@Entity
@Table(name = "motivo_disciplinar", schema = "adra",
        uniqueConstraints = @UniqueConstraint(name = "uq_motivo_disciplinar_nome_nivel", columnNames = {"nome", "nivel"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MotivoDisciplinar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "motivo_id")
    private Long motivoId;

    @Column(name = "nome", length = 150, nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", length = 20, nullable = false)
    private NivelDisciplinar nivel;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
