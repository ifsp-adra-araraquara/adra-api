package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "assistido", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assistido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assistido_id")
    private Long assistidoId;

    @Column(name = "nome_completo", length = 180, nullable = false)
    private String nomeCompleto;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "cpf", length = 20)
    private String cpf;

    @Column(name = "data_entrada", nullable = false)
    private LocalDate dataEntrada;

    @Column(name = "data_saida")
    private LocalDate dataSaida;

    @Column(name = "motivo_saida", columnDefinition = "text")
    private String motivoSaida;

    @Column(name = "necessidades_especificas", columnDefinition = "text")
    private String necessidadesEspecificas;

    @Column(name = "observacoes", columnDefinition = "text")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", length = 20)
    private Sexo sexo;

    // ---- Saúde (dado sensível - LGPD art. 11) ----
    @Column(name = "possui_problema_saude")
    private Boolean possuiProblemaSaude;

    @Column(name = "problema_saude", columnDefinition = "text")
    private String problemaSaude;

    @Column(name = "pcd")
    private Boolean pcd;

    @Column(name = "deficiencia", columnDefinition = "text")
    private String deficiencia;

    // ---- Escola ----
    @Column(name = "escola_nome", length = 180)
    private String escolaNome;

    @Column(name = "escola_ano_serie", length = 40)
    private String escolaAnoSerie;

    @Enumerated(EnumType.STRING)
    @Column(name = "escola_turno", length = 20)
    private Turno escolaTurno;

    @Column(name = "estuda_contraturno")
    private Boolean estudaContraturno;

    @Embedded
    private Endereco endereco;

    // ---- Socioeconômico (família) ----
    /** Renda mensal somada de quem mora na casa, em reais. */
    @Column(name = "renda_familiar", precision = 12, scale = 2)
    private BigDecimal rendaFamiliar;

    @Column(name = "recebe_programa_transferencia")
    private Boolean recebeProgramaTransferencia;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "assistido_programa_transferencia", schema = "adra",
            joinColumns = @JoinColumn(name = "assistido_id"))
    @Column(name = "programa", length = 30)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    @Builder.Default
    private Set<ProgramaTransferencia> programasTransferencia = new HashSet<>();

    @Column(name = "programa_transferencia_outro", length = 120)
    private String programaTransferenciaOutro;

    /** NIS / número do CadÚnico (11 dígitos). */
    @Column(name = "nis", length = 20)
    private String nis;

    // ---- Termos e autorizações ----
    @Column(name = "aceite_lgpd", nullable = false)
    @Builder.Default
    private boolean aceiteLgpd = false;

    /** Quando o aceite LGPD foi registrado (preenchido pelo back). */
    @Column(name = "aceite_lgpd_em")
    private OffsetDateTime aceiteLgpdEm;

    @Column(name = "autoriza_uso_imagem", nullable = false)
    @Builder.Default
    private boolean autorizaUsoImagem = false;

    @Column(name = "autoriza_eventos_passeios", nullable = false)
    @Builder.Default
    private boolean autorizaEventosPasseios = false;

    @Column(name = "pode_ir_embora_sozinho", nullable = false)
    @Builder.Default
    private boolean podeIrEmboraSozinho = false;

    @Column(name = "ciente_frequencia_obrigatoria", nullable = false)
    @Builder.Default
    private boolean cienteFrequenciaObrigatoria = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatusGeral status = StatusGeral.ATIVO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    // Contadores geridos pelo trigger trg_progressao_disciplinar (modulo de
    // disciplina, fora do escopo deste card). Nunca escrever por aqui.
    @Column(name = "total_ocorrencias_ativas", insertable = false, updatable = false)
    private Integer totalOcorrenciasAtivas;

    @Column(name = "total_advertencias_ativas", insertable = false, updatable = false)
    private Integer totalAdvertenciasAtivas;

    @Column(name = "total_suspensoes", insertable = false, updatable = false)
    private Integer totalSuspensoes;

    @CreationTimestamp
    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private OffsetDateTime atualizadoEm;

    @OneToMany(mappedBy = "assistido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AssistidoResponsavel> vinculosFamiliares = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (dataEntrada == null) {
            dataEntrada = LocalDate.now();
        }
        if (status == null) {
            status = StatusGeral.ATIVO;
        }
    }
}
