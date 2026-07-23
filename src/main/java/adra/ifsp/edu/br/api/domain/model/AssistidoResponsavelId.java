package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AssistidoResponsavelId implements Serializable {

    @Column(name = "assistido_id")
    private Long assistidoId;

    @Column(name = "responsavel_id")
    private Long responsavelId;
}
