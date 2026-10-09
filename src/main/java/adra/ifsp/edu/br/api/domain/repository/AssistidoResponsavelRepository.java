package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavelId;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
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

    /** Vínculos de vários assistidos com o responsável já carregado (sem N+1) — coluna "Responsável" da listagem. */
    @Query("SELECT ar FROM AssistidoResponsavel ar JOIN FETCH ar.responsavel WHERE ar.assistido.assistidoId IN :assistidoIds")
    List<AssistidoResponsavel> findComResponsavelByAssistidoIds(@Param("assistidoIds") Collection<Long> assistidoIds);
}
