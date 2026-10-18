package adra.ifsp.edu.br.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Endereço estruturado, embutido nas tabelas de assistido e responsável
 * (mesmos nomes de coluna nas duas). Se todas as colunas forem nulas, o
 * Hibernate devolve o campo {@code endereco} da entidade como null.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Endereco {

    @Column(name = "endereco_logradouro", length = 180)
    private String logradouro;

    @Column(name = "endereco_numero", length = 20)
    private String numero;

    @Column(name = "endereco_bairro", length = 120)
    private String bairro;

    @Column(name = "endereco_ponto_referencia", length = 255)
    private String pontoReferencia;
}
