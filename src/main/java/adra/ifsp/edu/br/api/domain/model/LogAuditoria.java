package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "log_auditoria", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_auditoria_id")
    private Long logAuditoriaId;

    // Nullable de proposito (ON DELETE SET NULL). Enquanto nao existir
    // autenticacao/JWT, gravamos null aqui - unico ponto a ajustar quando
    // o RBAC entrar (pegar o usuario autenticado do SecurityContext).
    @Column(name = "usuario_id")
    private Long usuarioId;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "modulo", nullable = false)
//    private ModuloSistema modulo;
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Column(name = "modulo", nullable = false)
private ModuloSistema modulo;

    @Column(name = "entidade_afetada", length = 100, nullable = false)
    private String entidadeAfetada;

    @Column(name = "entidade_id")
    private Long entidadeId;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "acao", nullable = false)
//    private AcaoSistema acao;
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Column(name = "acao", nullable = false)
private AcaoSistema acao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_anterior", columnDefinition = "jsonb")
    private Map<String, Object> valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_novo", columnDefinition = "jsonb")
    private Map<String, Object> valorNovo;

    @Column(name = "data_hora", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime dataHora;

    @Column(name = "ip", columnDefinition = "inet")
    private String ip;

    @Column(name = "dispositivo", columnDefinition = "text")
    private String dispositivo;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;
}
