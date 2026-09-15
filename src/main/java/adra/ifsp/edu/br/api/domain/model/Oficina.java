package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "oficina", schema = "adra")
public class Oficina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oficina_id")
    private Long oficinaId;

    @Column(name = "nome_oficina", nullable = false, length = 100)
    private String nomeOficina;

    /**
     * Campo descritivo (texto livre) - o oficineiro NAO e um usuario do
     * sistema (consistente com o MVP de 3 perfis: Administrador,
     * Coordenador, Sociopedagogico).
     */
    @Column(name = "oficineiro_responsavel", nullable = false, length = 150)
    private String oficineiroResponsavel;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void aoPersistir() {
        LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
        if (this.ativo == null) {
            this.ativo = true;
        }
    }

    @PreUpdate
    protected void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
