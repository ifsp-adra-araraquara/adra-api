package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.NivelPermissaoModulo;
import adra.ifsp.edu.br.api.domain.model.NivelPermissaoModuloId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NivelPermissaoModuloRepository extends JpaRepository<NivelPermissaoModulo, NivelPermissaoModuloId> {
    @Query("""
        SELECT npm FROM NivelPermissaoModulo npm
        JOIN FETCH npm.modulo m
        WHERE npm.id.nivelPermissaoId = :nivelPermissaoId and m.ativo = true
        ORDER BY npm.ordem ASC
        """)
    List<NivelPermissaoModulo> findComModuloById_NivelPermissaoIdOrderByOrdemAsc(@Param("nivelPermissaoId") Long nivelPermissaoId);

}
