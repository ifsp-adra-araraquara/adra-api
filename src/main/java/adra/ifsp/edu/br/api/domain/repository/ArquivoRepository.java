package adra.ifsp.edu.br.api.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}