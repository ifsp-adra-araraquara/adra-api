package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Entity
@Table(name = "turma", schema = "adra")
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turma_id")
    private Long turmaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oficina_id", nullable = false)
    private Oficina oficina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oficineiro_id", nullable = false)
    private Usuario oficineiroResponsavel;

    @Column(name = "nome_turma", nullable = false, length = 100)
    private String nomeTurma;

    @Enumerated(EnumType.STRING)
    @Column(name = "turno", nullable = false, length = 20)
    private Turno turno;

    @Column(name = "faixa_etaria", length = 50)
    private String faixaEtaria;

    @Column(name = "capacidade", nullable = false)
    private Integer capacidade;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "turma_dia_semana", schema = "adra", joinColumns = @JoinColumn(name = "turma_id"))
    @Column(name = "dia_semana")
    @Enumerated(EnumType.STRING)
    private List<DayOfWeek> diasDaSemana;

    @Column(name = "horario_inicio")
    private LocalTime horarioInicio;

    @Column(name = "horario_fim")
    private LocalTime horarioFim;

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