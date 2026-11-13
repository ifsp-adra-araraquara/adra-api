package adra.ifsp.edu.br.api.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import adra.ifsp.edu.br.api.domain.enums.StatusSolicitacaoDesligamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pedido de desligamento de um atendido: a Assistente Social pede, o Coordenador decide. */
@Entity
@Table(name = "solicitacao_desligamento", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
public class SolicitacaoDesligamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "solicitacao_id")
    private Long solicitacaoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assistido_id", nullable = false, updatable = false)
    private Assistido assistido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitado_por_id", nullable = false, updatable = false)
    private Usuario solicitadoPor;

    @Column(name = "data_saida", nullable = false)
    private LocalDate dataSaida;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSolicitacaoDesligamento status = StatusSolicitacaoDesligamento.PENDENTE;

    @Column(name = "resposta", length = 500)
    private String resposta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por_id")
    private Usuario decididoPor;

    @Column(name = "decidido_em")
    private OffsetDateTime decididoEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public static SolicitacaoDesligamento nova(Assistido assistido, Usuario solicitadoPor, LocalDate dataSaida, String motivo) {
        SolicitacaoDesligamento s = new SolicitacaoDesligamento();
        s.assistido = assistido;
        s.solicitadoPor = solicitadoPor;
        s.dataSaida = dataSaida;
        s.motivo = motivo;
        return s;
    }

    public void decidir(StatusSolicitacaoDesligamento decisao, String resposta, Usuario quem) {
        this.status = decisao;
        this.resposta = resposta;
        this.decididoPor = quem;
        this.decididoEm = OffsetDateTime.now();
    }

    public boolean pendente() {
        return status == StatusSolicitacaoDesligamento.PENDENTE;
    }
}
