package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatusGeral status = StatusGeral.ATIVO;

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
