package adra.ifsp.edu.br.api.domain.model;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chave que precisa sair do Storage e ainda não saiu (a linha do arquivo já foi apagada). */
@Entity
@Table(name = "arquivo_remocao_pendente", schema = "adra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArquivoRemocaoPendente {

    @Id
    @Column(name = "chave", length = 500)
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", length = 20, nullable = false)
    private ProvedorArmazenamento provedor;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", columnDefinition = "text")
    private String ultimoErro;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "tentado_em")
    private OffsetDateTime tentadoEm;

    public ArquivoRemocaoPendente(String chave, ProvedorArmazenamento provedor) {
        this.chave = chave;
        this.provedor = provedor;
    }

    public void falhou(String erro) {
        tentativas++;
        ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 1000));
        tentadoEm = OffsetDateTime.now();
    }
}
