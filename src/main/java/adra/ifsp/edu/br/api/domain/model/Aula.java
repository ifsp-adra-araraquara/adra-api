package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(
        name = "aula",
        schema = "adra",
        // evita duas aulas acidentais da mesma turma no mesmo dia
        uniqueConstraints = @UniqueConstraint(columnNames = {"turma_id", "data_aula"})
)
public class Aula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aula_id")
    private Long aulaId;

    /**
     * Aula pertence a turma agora (nao mais a oficina). Oficina e
     * obtida indiretamente via aula.getTurma().getOficina(), se precisar.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    // titulo NAO e obrigatorio - a aula pode nascer so com data+turma
    // no momento da chamada, e o titulo ser preenchido depois (ou nunca).
    @Column(name = "titulo", length = 150)
    private String titulo;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "data_aula", nullable = false)
    private LocalDate dataAula;

    @Column(name = "horario_inicio")
    private LocalTime horarioInicio;

    @Column(name = "horario_fim")
    private LocalTime horarioFim;

    @Column(name = "conteudo_previsto", columnDefinition = "TEXT")
    private String conteudoPrevisto;

    @Column(name = "conteudo_ministrado", columnDefinition = "TEXT")
    private String conteudoMinistrado;

    @Column(name = "objetivos", columnDefinition = "TEXT")
    private String objetivos;

    @Column(name = "recursos_necessarios", columnDefinition = "TEXT")
    private String recursosNecessarios;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_aula", nullable = false, length = 20)
    private StatusAula statusAula = StatusAula.PLANEJADA;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    /**
     * Quem deu (ou vai dar) esta aula. Fica gravado na aula para o histórico não
     * mudar quando a turma troca de oficineiro: aulas passadas mantêm o antigo,
     * só as planejadas de hoje em diante passam para o novo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oficineiro_id")
    private Usuario oficineiro;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void aoPersistir() {
        LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
        if (this.statusAula == null) {
            this.statusAula = StatusAula.PLANEJADA;
        }
        // Toda aula nasce com o oficineiro atual da turma.
        if (this.oficineiro == null && this.turma != null) {
            this.oficineiro = this.turma.getOficineiroResponsavel();
        }
    }

    @PreUpdate
    protected void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}