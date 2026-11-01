package adra.ifsp.edu.br.api.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.model.Arquivo;

public interface ArquivoRepository extends JpaRepository<Arquivo, Long> {

    Optional<Arquivo> findByArquivoIdAndExcluidoEmIsNull(Long arquivoId);

    List<Arquivo> findByEntidadeTipoAndEntidadeIdAndExcluidoEmIsNullOrderByCriadoEmDesc(
            EntidadeArquivo entidadeTipo, Long entidadeId);

    List<Arquivo> findByEntidadeTipoAndEntidadeIdAndCategoriaAndExcluidoEmIsNullOrderByCriadoEmDesc(
            EntidadeArquivo entidadeTipo, Long entidadeId, CategoriaArquivo categoria);

    /** Mesmo dono + categoria + conteúdo idêntico: reaproveita em vez de duplicar. */
    Optional<Arquivo> findFirstByEntidadeTipoAndEntidadeIdAndCategoriaAndHashSha256AndExcluidoEmIsNull(
            EntidadeArquivo entidadeTipo, Long entidadeId, CategoriaArquivo categoria, String hashSha256);

    /** Anexos de vários donos de uma vez (lista de pré-cadastros). */
    List<Arquivo> findByEntidadeTipoAndEntidadeIdInAndExcluidoEmIsNull(EntidadeArquivo entidadeTipo, Collection<Long> entidadeIds);

    /**
     * Troca o dono dos arquivos ativos (pré-cadastro aprovado). O dono é imutável na
     * entidade (updatable = false), por isso o update explícito.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Arquivo a SET a.entidadeTipo = :paraTipo, a.entidadeId = :paraId, a.categoria = :paraCategoria
            WHERE a.entidadeTipo = :deTipo AND a.entidadeId = :deId AND a.categoria = :deCategoria AND a.excluidoEm IS NULL
            """)
    int transferirDono(@Param("deTipo") EntidadeArquivo deTipo, @Param("deId") Long deId,
                       @Param("deCategoria") CategoriaArquivo deCategoria, @Param("paraTipo") EntidadeArquivo paraTipo,
                       @Param("paraId") Long paraId, @Param("paraCategoria") CategoriaArquivo paraCategoria);
}
