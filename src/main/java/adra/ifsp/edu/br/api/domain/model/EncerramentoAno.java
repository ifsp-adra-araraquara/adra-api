package adra.ifsp.edu.br.api.domain.model;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

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

/** Ano letivo encerrado pelo Coordenador, com o resumo do que aconteceu. */
@Entity
@Table(name = "encerramento_ano", schema = "adra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncerramentoAno {

    @Id
    @Column(name = "ano")
    private Integer ano;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encerrado_por_id")
    private Usuario encerradoPor;

    @CreationTimestamp
    @Column(name = "encerrado_em", nullable = false, updatable = false)
    private OffsetDateTime encerradoEm;

    @Column(name = "total_encerradas", nullable = false)
    private int totalEncerradas;

    @Column(name = "total_renovaram", nullable = false)
    private int totalRenovaram;

    @Column(name = "total_nao_renovaram", nullable = false)
    private int totalNaoRenovaram;

    public EncerramentoAno(int ano, Usuario encerradoPor, int totalEncerradas, int totalRenovaram, int totalNaoRenovaram) {
        this.ano = ano;
        this.encerradoPor = encerradoPor;
        this.totalEncerradas = totalEncerradas;
        this.totalRenovaram = totalRenovaram;
        this.totalNaoRenovaram = totalNaoRenovaram;
    }
}
