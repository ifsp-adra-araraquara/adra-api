package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssistidoRepository extends JpaRepository<Assistido, Long>, JpaSpecificationExecutor<Assistido> {

    /**
     * Usado na checagem de duplicidade do cadastro: mesmo nome completo
     * (case-insensitive) + mesma data de nascimento.
     * Ao editar, exclua o proprio id do resultado no service.
     */
    List<Assistido> findByNomeCompletoIgnoreCaseAndDataNascimento(String nomeCompleto, LocalDate dataNascimento);

    Optional<Assistido> findByCpf(String cpf);

    /**
     * Usado pra montar a contagem de "Alunos" nas telas de turma
     * (ex.: "Minhas turmas" do oficineiro).
     */
    long countByTurma_TurmaIdAndStatus(Long turmaId, StatusGeral status);

    /**
     * Contagem de matriculados por turma, para várias turmas de uma vez (uma
     * query agrupada em vez de uma COUNT por turma) — usado na listagem geral
     * de Turmas, que precisa mostrar ocupação sem virar N+1 na tabela.
     */
    @Query("""
            SELECT a.turma.turmaId, COUNT(a)
            FROM Assistido a
            WHERE a.turma.turmaId IN :turmaIds AND a.status = :status
            GROUP BY a.turma.turmaId
            """)
    List<Object[]> contarAtivosPorTurma(@Param("turmaIds") List<Long> turmaIds, @Param("status") StatusGeral status);

    /**
     * FT-04: recalcula os contadores disciplinares a partir dos registros não
     * arquivados. Conta do zero em vez de somar/subtrair, então registrar,
     * arquivar ou corrigir sempre deixa o valor certo. Os campos são
     * somente leitura na entidade, por isso o update é nativo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE adra.assistido SET
                total_ocorrencias_ativas  = (SELECT count(*) FROM adra.registro_disciplinar r
                                              WHERE r.assistido_id = :assistidoId AND r.arquivado = false AND r.nivel = 'OCORRENCIA'),
                total_advertencias_ativas = (SELECT count(*) FROM adra.registro_disciplinar r
                                              WHERE r.assistido_id = :assistidoId AND r.arquivado = false AND r.nivel = 'ADVERTENCIA'),
                total_suspensoes          = (SELECT count(*) FROM adra.registro_disciplinar r
                                              WHERE r.assistido_id = :assistidoId AND r.arquivado = false AND r.nivel = 'SUSPENSAO')
            WHERE assistido_id = :assistidoId
            """, nativeQuery = true)
    void recalcularContadoresDisciplinares(@Param("assistidoId") Long assistidoId);
}
