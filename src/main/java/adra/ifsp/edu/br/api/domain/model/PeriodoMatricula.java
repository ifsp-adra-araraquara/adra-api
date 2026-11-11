package adra.ifsp.edu.br.api.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Período em que o link público recebe inscrições (novas e rematrículas) para um ano letivo. */
@Entity
@Table(name = "periodo_matricula", schema = "adra")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeriodoMatricula {

    @Id
    @Column(name = "ano")
    private Integer ano;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    /** null = aberto até o Coordenador fechar. */
    @Column(name = "fim")
    private LocalDate fim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por_id")
    private Usuario alteradoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public PeriodoMatricula(Integer ano) {
        this.ano = ano;
    }

    public boolean abertoEm(LocalDate dia) {
        return !dia.isBefore(inicio) && (fim == null || !dia.isAfter(fim));
    }
}
