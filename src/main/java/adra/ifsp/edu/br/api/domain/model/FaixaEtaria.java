package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** Faixa etária dos grupos, cadastrável pela coordenação. Faixas podem se sobrepor. */
@Entity
@Table(name = "faixa_etaria", schema = "adra",
        uniqueConstraints = @UniqueConstraint(name = "uq_faixa_etaria_intervalo", columnNames = {"idade_minima", "idade_maxima"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaixaEtaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faixa_etaria_id")
    private Long faixaEtariaId;

    @Column(name = "idade_minima", nullable = false)
    private Short idadeMinima;

    @Column(name = "idade_maxima", nullable = false)
    private Short idadeMaxima;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public String getRotulo() {
        return rotulo(idadeMinima, idadeMaxima);
    }

    public static String rotulo(Number idadeMinima, Number idadeMaxima) {
        return idadeMinima + "–" + idadeMaxima + " anos";
    }
}
