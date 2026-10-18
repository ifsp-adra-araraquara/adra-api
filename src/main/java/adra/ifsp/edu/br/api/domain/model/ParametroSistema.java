package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Parâmetros de regra de negócio configuráveis no banco (sem deploy).
 * Ex.: {@link #RENDA_FAMILIAR_LIMITE} — corte de renda familiar para participar do programa.
 */
@Entity
@Table(name = "parametro_sistema", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametroSistema {

    public static final String RENDA_FAMILIAR_LIMITE = "RENDA_FAMILIAR_LIMITE";

    @Id
    @Column(name = "chave", length = 60)
    private String chave;

    /** null = parâmetro ainda não definido (regra desligada). */
    @Column(name = "valor_numerico", precision = 12, scale = 2)
    private BigDecimal valorNumerico;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;
}
