package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Semanario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SemanarioRepository extends JpaRepository<Semanario, Long> {

    Optional<Semanario> findByOficina_OficinaIdAndTurma_TurmaIdAndSemanaInicio(Long oficinaId, Long turmaId, LocalDate semanaInicio);

    List<Semanario> findByOficina_OficinaIdOrderBySemanaInicioDesc(Long oficinaId);

    List<Semanario> findByOficina_OficineiroResponsavel_UsuarioIdOrderBySemanaInicioDesc(Long oficineiroId);

    /** O semanário é de uma oficina deste oficineiro? (ver AcessoTurmaService) */
    boolean existsBySemanarioIdAndOficina_OficineiroResponsavel_UsuarioId(Long semanarioId, Long oficineiroId);

    /** Oficina ativa com oficineiro e quantos grupos ativos dela ainda não têm o semanário da semana. */
    interface SemanarioPendente {
        Long getOficinaId();

        String getNomeOficina();

        long getGrupos();
    }

    @Query("""
            SELECT o.oficinaId AS oficinaId, o.nomeOficina AS nomeOficina, COUNT(ot) AS grupos
            FROM OficinaTurma ot JOIN ot.oficina o JOIN ot.turma t
            WHERE ot.ativo = true AND o.ativo = true AND t.ativo = true
              AND o.oficineiroResponsavel IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM Semanario s
                              WHERE s.oficina = o AND s.turma = t AND s.semanaInicio = :semana)
            GROUP BY o.oficinaId, o.nomeOficina
            ORDER BY o.nomeOficina
            """)
    List<SemanarioPendente> pendentesDaSemana(@Param("semana") LocalDate semana);
}
