package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Plano da semana que o oficineiro envia para um grupo atendido pela oficina. Um por semana. */
@Entity
@Table(name = "semanario", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
public class Semanario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "semanario_id")
    private Long semanarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oficina_id")
    private Oficina oficina;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    /** Segunda-feira da semana. */
    @Column(name = "semana_inicio", nullable = false)
    private LocalDate semanaInicio;

    @Column(name = "conteudo_previsto", nullable = false, columnDefinition = "TEXT")
    private String conteudoPrevisto;

    @Column(name = "objetivos", columnDefinition = "TEXT")
    private String objetivos;

    @Column(name = "recursos_necessarios", columnDefinition = "TEXT")
    private String recursosNecessarios;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enviado_por_id")
    private Usuario enviadoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
