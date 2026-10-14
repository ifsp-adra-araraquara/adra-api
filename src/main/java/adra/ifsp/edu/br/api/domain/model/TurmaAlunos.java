package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Histórico de vínculo do assistido com a turma. Necessário porque o assistido
 * pode trocar de turma ao longo do tempo — sem isso não dá pra saber quem
 * pertencia à turma na data de uma aula específica.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "turma_aluno", schema = "adra")
public class TurmaAlunos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turma_aluno_id")
    private Long turmaAlunoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistido_id", nullable = false)
    private Assistido assistido;

    @Column(name = "data_entrada", nullable = false)
    private LocalDate dataEntrada;

    @Column(name = "data_saida")
    private LocalDate dataSaida;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusGeral status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}