package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
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
 * Rascunho enviado pela família via link público (sem login), antes de virar
 * um assistido de verdade. Nunca é a mesma linha que {@link Assistido} — a
 * equipe revisa e cria o cadastro oficial manualmente (onde CPF passa a ser
 * exigido, igual já é hoje).
 */
@Entity
@Table(name = "pre_cadastro", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreCadastro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pre_cadastro_id")
    private Long preCadastroId;

    @Column(name = "nome_completo", length = 180, nullable = false)
    private String nomeCompleto;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "responsavel_nome", length = 180, nullable = false)
    private String responsavelNome;

    @Column(name = "responsavel_telefone", length = 20)
    private String responsavelTelefone;

    @Column(name = "endereco", length = 255)
    private String endereco;

    @Column(name = "observacao", columnDefinition = "TEXT")
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private StatusPreCadastro status = StatusPreCadastro.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por_id")
    private Usuario revisadoPor;

    @Column(name = "revisado_em")
    private OffsetDateTime revisadoEm;

    @Column(name = "ip_origem", length = 64)
    private String ipOrigem;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
}
