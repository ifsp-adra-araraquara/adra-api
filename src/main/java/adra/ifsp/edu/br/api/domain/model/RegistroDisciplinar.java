package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.OrigemRegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Um fato disciplinar do assistido (ocorrência, advertência, suspensão ou
 * uniforme). Nunca é excluído — só arquivado pelo Coordenador. Registros
 * gerados por progressão apontam para o registro que os disparou.
 */
@Entity
@Table(name = "registro_disciplinar", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroDisciplinar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registro_id")
    private Long registroId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistido_id", nullable = false, updatable = false)
    private Assistido assistido;

    /** Null quando gerado por progressão. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_id", updatable = false)
    private MotivoDisciplinar motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", length = 20, nullable = false, updatable = false)
    private NivelDisciplinar nivel;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", length = 20, nullable = false, updatable = false)
    @Builder.Default
    private OrigemRegistroDisciplinar origem = OrigemRegistroDisciplinar.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_origem_id", updatable = false)
    private RegistroDisciplinar registroOrigem;

    /** 1ª, 2ª, 3ª... entre os ativos do mesmo nível no momento do registro (usado no texto do documento). */
    @Column(name = "sequencia", nullable = false, updatable = false)
    private Integer sequencia;

    @Column(name = "data_registro", nullable = false)
    private LocalDate dataRegistro;

    @Column(name = "descricao", nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id")
    private Aula aula;

    @Column(name = "suspensao_inicio")
    private LocalDate suspensaoInicio;

    @Column(name = "suspensao_fim")
    private LocalDate suspensaoFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_status", length = 30)
    private StatusDocumentoDisciplinar documentoStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_arquivo_id")
    private Arquivo documentoArquivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "consequencia", length = 30)
    private ConsequenciaDisciplinar consequencia;

    @Column(name = "consequencia_resolvida_em")
    private LocalDate consequenciaResolvidaEm;

    @Column(name = "consequencia_observacao", columnDefinition = "TEXT")
    private String consequenciaObservacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consequencia_resolvida_por_id")
    private Usuario consequenciaResolvidaPor;

    @Column(name = "arquivado", nullable = false)
    @Builder.Default
    private boolean arquivado = false;

    @Column(name = "arquivado_motivo", columnDefinition = "TEXT")
    private String arquivadoMotivo;

    @Column(name = "arquivado_em")
    private OffsetDateTime arquivadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arquivado_por_id")
    private Usuario arquivadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = false, updatable = false)
    private Usuario registradoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    /** Consequência que ainda espera ação da coordenação (passeio perdido, conversa com o responsável). */
    public boolean temConsequenciaPendente() {
        return !arquivado && consequencia != null && consequencia.exigeResolucao() && consequenciaResolvidaEm == null;
    }

    public boolean suspensoEm(LocalDate data) {
        return !arquivado && nivel == NivelDisciplinar.SUSPENSAO
                && !data.isBefore(suspensaoInicio) && !data.isAfter(suspensaoFim);
    }
}
