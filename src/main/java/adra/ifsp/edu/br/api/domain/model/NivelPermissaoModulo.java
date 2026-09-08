package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "nivel_permissao_modulo", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NivelPermissaoModulo {

    @EmbeddedId
    private NivelPermissaoModuloId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("nivelPermissaoId")
    @JoinColumn(name = "nivel_permissao_id")
    private NivelPermissao nivelPermissao;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("moduloId")
    @JoinColumn(name = "modulo_id")
    private Modulo modulo;

    @Column(name = "ordem", nullable = false)
    @Builder.Default
    private Integer ordem = 0;

    @Column(name = "eh_padrao", nullable = false)
    @Builder.Default
    private boolean ehPadrao = false;
}