package adra.ifsp.edu.br.api.domain.model;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Metadados de um arquivo guardado no storage. O binário NUNCA vai pro banco.
 *
 * Dono polimórfico (entidadeTipo + entidadeId), sem FK: a existência do dono
 * é validada no ArquivoService. Arquivos não são editados — trocar a foto cria
 * uma linha nova e marca a anterior como excluída.
 */
@Entity
@Table(name = "arquivo", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Arquivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "arquivo_id")
    private Long arquivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entidade_tipo", length = 40, nullable = false, updatable = false)
    private EntidadeArquivo entidadeTipo;

    @Column(name = "entidade_id", nullable = false, updatable = false)
    private Long entidadeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 40, nullable = false, updatable = false)
    private CategoriaArquivo categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", length = 20, nullable = false)
    private ProvedorArmazenamento provedor;

    /** Caminho no bucket (ex.: assistido/123/foto_perfil/uuid.webp). Não é URL. */
    @Column(name = "chave", length = 500, nullable = false, unique = true)
    private String chave;

    @Column(name = "nome_original", length = 255)
    private String nomeOriginal;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Column(name = "hash_sha256", length = 64, nullable = false, columnDefinition = "char(64)")
    private String hashSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enviado_por_id", updatable = false)
    private Usuario enviadoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    public boolean isAtivo() {
        return excluidoEm == null;
    }

    public void marcarComoExcluido() {
        this.excluidoEm = LocalDateTime.now();
    }
}