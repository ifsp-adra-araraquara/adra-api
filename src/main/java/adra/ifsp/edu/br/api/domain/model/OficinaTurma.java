package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Grupo (turma) atendido por uma oficina. Desfazer o vínculo inativa a linha. */
@Entity
@Table(name = "oficina_turma", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
public class OficinaTurma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oficina_turma_id")
    private Long oficinaTurmaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oficina_id")
    private Oficina oficina;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    public OficinaTurma(Oficina oficina, Turma turma) {
        this.oficina = oficina;
        this.turma = turma;
    }
}
