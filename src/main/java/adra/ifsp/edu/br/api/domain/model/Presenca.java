package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "presenca",
        schema = "adra",
        // evita registrar presenca duplicada do mesmo assistido na mesma aula
        uniqueConstraints = @UniqueConstraint(columnNames = {"aula_id", "assistido_id"})
)
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "presenca_id")
    private Long presencaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;

    /**
     * Assumindo que ja existe (ou existira) uma entidade Assistido no
     * pacote domain.model. Se o nome/campos forem diferentes no projeto
     * de voces, so ajustar esse relacionamento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistido_id", nullable = false)
    private Assistido assistido;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_presenca", nullable = false, length = 20)
    private StatusPresenca statusPresenca;

    @Column(name = "justificativa_falta", columnDefinition = "TEXT")
    private String justificativaFalta;

    @Column(name = "observacao_do_dia", columnDefinition = "TEXT")
    private String observacaoDoDia;

    @Column(name = "horario_registro", nullable = false, updatable = false)
    private LocalDateTime horarioRegistro;

    @PrePersist
    protected void aoPersistir() {
        this.horarioRegistro = LocalDateTime.now();
    }
}