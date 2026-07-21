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
 * Classe associativa N:N entre Assistido e Responsavel. Representa o
 * vinculo familiar (substitui a antiga entidade "Familia" da REV 1).
 *
 * Regra critica: no maximo 1 responsavel_principal=true por assistido.
 * Garantida por indice unico parcial no banco (assistido_um_principal_uk)
 * E validada preventivamente no service, para retornar um erro de negocio
 * legivel em vez de estourar uma constraint no banco.
 */
@Entity
@Table(name = "assistido_responsavel", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistidoResponsavel {

    @EmbeddedId
    private AssistidoResponsavelId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("assistidoId")
    @JoinColumn(name = "assistido_id")
    private Assistido assistido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("responsavelId")
    @JoinColumn(name = "responsavel_id")
    private Responsavel responsavel;

    @Column(name = "parentesco", length = 80)
    private String parentesco;

    @Column(name = "responsavel_principal", nullable = false)
    @Builder.Default
    private boolean responsavelPrincipal = false;

    @Column(name = "contato_emergencia", nullable = false)
    @Builder.Default
    private boolean contatoEmergencia = false;

    @Column(name = "autorizado_retirada", nullable = false)
    @Builder.Default
    private boolean autorizadoRetirada = false;

    @Column(name = "observacoes", columnDefinition = "text")
    private String observacoes;

    @CreationTimestamp
    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
