package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "invitacao_usuario", schema = "adra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitacaoUsuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitacao_id")
    private Long invitacaoId;
    
    @Column(name = "email", nullable = false)
    private String email;
    
    @Column(name = "token", unique = true, nullable = false)
    private String token;
    
    @Column(name = "validade", nullable = false)
    private LocalDateTime validadeAte;
    
    @Column(name = "consumido", nullable = false)
    @Builder.Default
    private boolean consumido = false;
    
    @Column(name = "consumido_em")
    private LocalDateTime consumidoEm;
    
    @CreationTimestamp
    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;
}