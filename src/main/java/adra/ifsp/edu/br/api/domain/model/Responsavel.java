package adra.ifsp.edu.br.api.domain.model;

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

/**
 * Sem coluna de status/ativo no schema atual (diferente de Assistido).
 * Por isso nao ha endpoint de "inativar" responsavel - ver comentario
 * no ResponsavelController sobre a decisao de nao implementar hard delete.
 */
@Entity
@Table(name = "responsavel", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Responsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "responsavel_id")
    private Long responsavelId;

    @Column(name = "nome_completo", length = 180, nullable = false)
    private String nomeCompleto;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "cpf", length = 20)
    private String cpf;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "endereco", columnDefinition = "text")
    private String endereco;

    @Column(name = "observacoes", columnDefinition = "text")
    private String observacoes;

    @CreationTimestamp
    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private OffsetDateTime atualizadoEm;

    @OneToMany(mappedBy = "responsavel", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AssistidoResponsavel> vinculosFamiliares = new ArrayList<>();
}
