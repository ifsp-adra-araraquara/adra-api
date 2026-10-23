package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Período em que um oficineiro foi o responsável pela turma.
 * data_fim nula = responsável atual. Trocar o oficineiro fecha o período
 * aberto e abre outro — nunca sobrescreve (rastreabilidade de quem deu aula quando).
 */
@Entity
@Table(name = "turma_oficineiro_historico", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurmaOficineiroHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historico_id")
    private Long historicoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oficineiro_id")
    private Usuario oficineiro;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;
}
