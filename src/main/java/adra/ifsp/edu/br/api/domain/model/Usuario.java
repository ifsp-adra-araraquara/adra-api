package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.EspecialidadeSaude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * IMPORTANTE: nunca deletar fisicamente (comentario original do schema:
 * "Usuario nunca deve ser deletado fisicamente, usar ativo=false"). Por
 * isso nao ha metodo/endpoint de exclusao - so' alteracao de status.
 */
@Entity
@Table(name = "usuario", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nivel_permissao_id")
    private NivelPermissao nivelPermissao;

    @Column(name = "nome_completo", length = 180, nullable = false)
    private String nomeCompleto;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    // Nunca exposto em DTO de resposta - so' o mapper/service tocam nisso.
    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "cargo_funcao", length = 120)
    private String cargoFuncao;

    @Column(name = "telefone", length = 30)
    private String telefone;

    // So' preenchido quando nivelPermissao == PROFISSIONAL_SAUDE (regra
    // validada no UsuarioService, coluna criada na migracao deste card).
    @Enumerated(EnumType.STRING)
    @Column(name = "especialidade")
    private EspecialidadeSaude especialidade;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "ultimo_login")
    private OffsetDateTime ultimoLogin;

    @CreationTimestamp
    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private OffsetDateTime atualizadoEm;
}
