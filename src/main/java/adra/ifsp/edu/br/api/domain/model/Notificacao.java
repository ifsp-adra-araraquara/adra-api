package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.enums.TipoEnvolvido;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FT-14: uma notificação para um destinatário. O mesmo fato avisado a vários
 * Coordenadores vira uma linha para cada um, e cada um marca a sua como lida.
 * {@code referencia} identifica o fato dentro do tipo (ex.: "assistido:12:desde:2026-11-03");
 * o banco não deixa repetir (tipo, referencia, destinatario).
 */
@Entity
@Table(name = "notificacao", schema = "adra",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "referencia", "destinatario_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notificacao_id")
    private Long notificacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 40, nullable = false)
    private TipoAviso tipo;

    @Column(name = "referencia", length = 120, nullable = false)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinatario_id")
    private Usuario destinatario;

    @Column(name = "titulo", length = 200, nullable = false)
    private String titulo;

    @Column(name = "mensagem", length = 500)
    private String mensagem;

    /** Rota do front onde a notificação se resolve (ex.: "/assistidos?assistidoId=12&aba=cham"). */
    @Column(name = "link", length = 255)
    private String link;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "lida_em")
    private OffsetDateTime lidaEm;

    @ElementCollection
    @CollectionTable(name = "notificacao_envolvido", schema = "adra", joinColumns = @JoinColumn(name = "notificacao_id"))
    private Set<Envolvido> envolvidos = new HashSet<>();

    @OneToMany(mappedBy = "notificacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificacaoEntrega> entregas = new ArrayList<>();

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
    }

    /** Quem a notificação envolve; o id é da tabela indicada pelo tipo. */
    @Embeddable
    public record Envolvido(
            @Enumerated(EnumType.STRING) @Column(name = "tipo_envolvido", length = 20, nullable = false) TipoEnvolvido tipo,
            @Column(name = "envolvido_id", nullable = false) Long id) {

        public static Envolvido assistido(Long id) {
            return new Envolvido(TipoEnvolvido.ASSISTIDO, id);
        }

        public static Envolvido turma(Long id) {
            return new Envolvido(TipoEnvolvido.TURMA, id);
        }

        public static Envolvido oficina(Long id) {
            return new Envolvido(TipoEnvolvido.OFICINA, id);
        }

        public static Envolvido usuario(Long id) {
            return new Envolvido(TipoEnvolvido.USUARIO, id);
        }
    }
}
