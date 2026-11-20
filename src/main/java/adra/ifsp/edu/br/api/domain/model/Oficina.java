package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.BatchSize;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "oficina", schema = "adra")
public class Oficina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oficina_id")
    private Long oficinaId;

    @Column(name = "nome_oficina", nullable = false, length = 100)
    private String nomeOficina;

    /**
     * Campo descritivo (texto livre) - o oficineiro NAO e um usuario do
     * sistema (consistente com o MVP de 3 perfis: Administrador,
     * Coordenador, Sociopedagogico).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oficineiro_id", nullable = false)
    private Usuario oficineiroResponsavel;

    /** Onde a oficina acontece (ex.: "Sala 1", "Quadra"). Opcional. */
    @Column(name = "local", length = 100)
    private String local;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "oficina_dia_semana", schema = "adra", joinColumns = @JoinColumn(name = "oficina_id"))
    @Column(name = "dia_semana")
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private List<DayOfWeek> diasDaSemana;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void aoPersistir() {
        LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
        if (this.ativo == null) {
            this.ativo = true;
        }
    }

    @PreUpdate
    protected void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
