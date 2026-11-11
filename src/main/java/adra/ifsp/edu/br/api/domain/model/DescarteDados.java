package adra.ifsp.edu.br.api.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Registro de um descarte por prazo de guarda: só números, nenhum dado pessoal. */
@Entity
@Table(name = "descarte_dados", schema = "adra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DescarteDados {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "descarte_id")
    private Long descarteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executado_por_id")
    private Usuario executadoPor;

    @CreationTimestamp
    @Column(name = "executado_em", nullable = false, updatable = false)
    private OffsetDateTime executadoEm;

    @Column(name = "limite", nullable = false)
    private LocalDate limite;

    @Column(name = "matriculas", nullable = false)
    private int matriculas;

    @Column(name = "atendidos", nullable = false)
    private int atendidos;

    @Column(name = "responsaveis", nullable = false)
    private int responsaveis;

    @Column(name = "pre_cadastros", nullable = false)
    private int preCadastros;

    @Column(name = "arquivos", nullable = false)
    private int arquivos;

    @Column(name = "bytes_liberados", nullable = false)
    private long bytesLiberados;

    public DescarteDados(Usuario executadoPor, LocalDate limite, int matriculas, int atendidos, int responsaveis,
                         int preCadastros, int arquivos, long bytesLiberados) {
        this.executadoPor = executadoPor;
        this.limite = limite;
        this.matriculas = matriculas;
        this.atendidos = atendidos;
        this.responsaveis = responsaveis;
        this.preCadastros = preCadastros;
        this.arquivos = arquivos;
        this.bytesLiberados = bytesLiberados;
    }
}
