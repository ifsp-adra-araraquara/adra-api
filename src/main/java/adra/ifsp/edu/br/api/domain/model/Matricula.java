package adra.ifsp.edu.br.api.domain.model;

import java.time.OffsetDateTime;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import adra.ifsp.edu.br.api.domain.enums.StatusMatricula;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Matrícula de um atendido num ano letivo. Tudo o que a família refaz todo ano
 * (termos, autorizações, formulário enviado) mora aqui; a ficha não é sobrescrita.
 *
 * <p>{@code @BatchSize}: listagens e chamada carregam a matrícula atual de vários
 * atendidos de uma vez, em vez de uma consulta por atendido.
 */
@Entity
@Table(name = "matricula", schema = "adra",
        uniqueConstraints = @UniqueConstraint(name = "uq_matricula_assistido_ano", columnNames = {"assistido_id", "ano"}))
@BatchSize(size = 50)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "matricula_id")
    private Long matriculaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assistido_id", nullable = false, updatable = false)
    private Assistido assistido;

    @Column(name = "ano", nullable = false, updatable = false)
    private Integer ano;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusMatricula status = StatusMatricula.ATIVA;

    /** Envio da família que gerou esta matrícula (null no cadastro feito pela equipe). */
    @Column(name = "pre_cadastro_id")
    private Long preCadastroId;

    @Column(name = "aceite_lgpd", nullable = false)
    private boolean aceiteLgpd;

    @Column(name = "aceite_lgpd_em")
    private OffsetDateTime aceiteLgpdEm;

    @Column(name = "autoriza_uso_imagem", nullable = false)
    private boolean autorizaUsoImagem;

    @Column(name = "autoriza_eventos_passeios", nullable = false)
    private boolean autorizaEventosPasseios;

    @Column(name = "pode_ir_embora_sozinho", nullable = false)
    private boolean podeIrEmboraSozinho;

    @Column(name = "ciente_frequencia_obrigatoria", nullable = false)
    private boolean cienteFrequenciaObrigatoria;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public static Matricula nova(Assistido assistido, int ano) {
        Matricula matricula = new Matricula();
        matricula.assistido = assistido;
        matricula.ano = ano;
        return matricula;
    }

    /** Desligou no meio do ano: a matrícula do ano fecha junto. */
    public void desligar() {
        if (status == StatusMatricula.ATIVA) {
            status = StatusMatricula.DESLIGADA;
        }
    }

    /** Reativou o atendido: a matrícula volta a valer (só se ela foi desligada, não encerrada). */
    public void reativar() {
        if (status == StatusMatricula.DESLIGADA) {
            status = StatusMatricula.ATIVA;
        }
    }
}
