package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavelId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssistidoResponsavelRepository extends JpaRepository<AssistidoResponsavel, AssistidoResponsavelId> {

    List<AssistidoResponsavel> findByIdAssistidoId(Long assistidoId);

    List<AssistidoResponsavel> findByIdResponsavelId(Long responsavelId);

    Optional<AssistidoResponsavel> findByIdAssistidoIdAndIdResponsavelId(Long assistidoId, Long responsavelId);

    /**
     * Existe algum vinculo principal para este assistido, diferente do
     * proprio responsavel informado? Usado para validar a regra de
     * "no maximo 1 responsavel principal por assistido" antes de gravar,
     * tanto na criacao quanto na edicao do vinculo.
     */
    boolean existsByIdAssistidoIdAndResponsavelPrincipalTrueAndIdResponsavelIdNot(Long assistidoId, Long responsavelId);
}
