package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Tabela de referencia (6 linhas fixas, uma por perfil). Populada pela
 * migracao deste card (migracao_cadastrar_usuario.sql) via INSERT ...
 * ON CONFLICT DO NOTHING - nunca criada/editada pela aplicacao.
 */
@Entity
@Table(name = "nivel_permissao", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NivelPermissao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nivel_permissao_id")
    private Long nivelPermissaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nome", nullable = false, unique = true)
    private NomeNivelPermissao nome;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private OffsetDateTime atualizadoEm;
}
