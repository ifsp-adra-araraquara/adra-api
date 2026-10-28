package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.RegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RegistroDisciplinarRepository extends JpaRepository<RegistroDisciplinar, Long>,
        JpaSpecificationExecutor<RegistroDisciplinar> {

    /** Lista geral da tela Disciplinar, já com assistido, motivo e autor (evita N+1 na página). */
    @Override
    @EntityGraph(attributePaths = {"assistido", "motivo", "registradoPor"})
    Page<RegistroDisciplinar> findAll(Specification<RegistroDisciplinar> spec, Pageable pageable);

    // ---- Resumo de pendências (cartões do topo da tela Disciplinar) ----

    @Query("""
            SELECT count(DISTINCT r.assistido.assistidoId) FROM RegistroDisciplinar r
            WHERE r.arquivado = false
              AND r.nivel = adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar.SUSPENSAO
              AND r.suspensaoInicio <= :data AND r.suspensaoFim >= :data
            """)
    long contarAssistidosSuspensosEm(@Param("data") LocalDate data);

    @Query("""
            SELECT count(DISTINCT r.assistido.assistidoId) FROM RegistroDisciplinar r
            WHERE r.arquivado = false AND r.consequencia = :consequencia AND r.consequenciaResolvidaEm IS NULL
            """)
    long contarAssistidosComConsequenciaPendente(@Param("consequencia") ConsequenciaDisciplinar consequencia);

    long countByArquivadoFalseAndDocumentoStatus(StatusDocumentoDisciplinar documentoStatus);


    long countByAssistidoAndNivelAndArquivadoFalse(Assistido assistido, NivelDisciplinar nivel);

    /** Histórico da ficha, mais recente primeiro, já com motivo e autores (evita N+1). */
    @Query("""
            SELECT r FROM RegistroDisciplinar r
            LEFT JOIN FETCH r.motivo
            LEFT JOIN FETCH r.registradoPor
            LEFT JOIN FETCH r.arquivadoPor
            LEFT JOIN FETCH r.consequenciaResolvidaPor
            LEFT JOIN FETCH r.aula
            WHERE r.assistido = :assistido
            ORDER BY r.dataRegistro DESC, r.registroId DESC
            """)
    List<RegistroDisciplinar> findHistorico(@Param("assistido") Assistido assistido);

    /**
     * Registros que podem gerar sinalização (suspensão ou consequência
     * pendente) para vários assistidos de uma vez — chamada e listagem.
     */
    @Query("""
            SELECT r FROM RegistroDisciplinar r
            WHERE r.assistido.assistidoId IN :assistidoIds
              AND r.arquivado = false
              AND (r.nivel = adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar.SUSPENSAO
                   OR (r.consequencia IS NOT NULL AND r.consequenciaResolvidaEm IS NULL))
            """)
    List<RegistroDisciplinar> findCandidatosASinalizacao(@Param("assistidoIds") Collection<Long> assistidoIds);
}
