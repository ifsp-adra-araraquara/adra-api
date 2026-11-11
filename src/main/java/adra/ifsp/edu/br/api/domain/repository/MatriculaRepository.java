package adra.ifsp.edu.br.api.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import adra.ifsp.edu.br.api.domain.model.Matricula;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    Optional<Matricula> findByAssistido_AssistidoIdAndAno(Long assistidoId, Integer ano);

    /** Histórico do atendido, do ano mais recente para o mais antigo. */
    List<Matricula> findByAssistido_AssistidoIdOrderByAnoDesc(Long assistidoId);

    // ---------------------------------------------------------------
    // Encerramento do ano: prévia e atualizações em lote (SQL direto,
    // poucas instruções para o ano inteiro em vez de uma por atendido).
    // ---------------------------------------------------------------

    /** Atendido que estava ativo no ano e não tem matrícula no seguinte. */
    interface NaoRenovou {
        Long getAssistidoId();

        String getNomeCompleto();

        String getNomeTurma();
    }

    @Query(value = "select count(*) from adra.matricula where ano = :ano and status = 'ATIVA'", nativeQuery = true)
    int contarAtivas(@Param("ano") int ano);

    @Query(value = """
            select count(*) from adra.matricula m
            where m.ano = :ano and m.status = 'ATIVA'
              and exists (select 1 from adra.matricula s where s.assistido_id = m.assistido_id and s.ano = :ano + 1)
            """, nativeQuery = true)
    int contarRenovaram(@Param("ano") int ano);

    /** Matrículas do ano seguinte de quem não tinha matrícula no ano (famílias novas). */
    @Query(value = """
            select count(*) from adra.matricula s
            where s.ano = :ano + 1
              and not exists (select 1 from adra.matricula m where m.assistido_id = s.assistido_id and m.ano = :ano)
            """, nativeQuery = true)
    int contarNovosNoAnoSeguinte(@Param("ano") int ano);

    @Query(value = """
            select a.assistido_id as assistidoId, a.nome_completo as nomeCompleto, t.nome_turma as nomeTurma
            from adra.matricula m
            join adra.assistido a on a.assistido_id = m.assistido_id
            left join adra.turma t on t.turma_id = a.turma_id
            where m.ano = :ano and m.status = 'ATIVA'
              and not exists (select 1 from adra.matricula s where s.assistido_id = m.assistido_id and s.ano = :ano + 1)
            order by a.nome_completo
            """, nativeQuery = true)
    List<NaoRenovou> listarNaoRenovaram(@Param("ano") int ano);

    /** Quem não renovou perde a vaga: sai da ADRA no último dia do ano. Rodar antes de encerrar as matrículas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.assistido a
               set status = 'INATIVO', data_saida = :ultimoDia, motivo_saida = :motivo, turma_id = null,
                   atualizado_em = now()
             where a.status = 'ATIVO'
               and exists (select 1 from adra.matricula m
                           where m.assistido_id = a.assistido_id and m.ano = :ano and m.status = 'ATIVA')
               and not exists (select 1 from adra.matricula s where s.assistido_id = a.assistido_id and s.ano = :ano + 1)
            """, nativeQuery = true)
    int desligarQuemNaoRenovou(@Param("ano") int ano, @Param("ultimoDia") LocalDate ultimoDia,
                               @Param("motivo") String motivo);

    /** Quem tem matrícula no ano seguinte passa a ter ela como atual, com o grupo dela. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.assistido a
               set matricula_atual_id = s.matricula_id,
                   turma_id = (select ta.turma_id from adra.turma_aluno ta
                               where ta.matricula_id = s.matricula_id and ta.data_saida is null
                               order by ta.data_entrada desc limit 1),
                   atualizado_em = now()
              from adra.matricula s
             where s.assistido_id = a.assistido_id and s.ano = :ano + 1
            """, nativeQuery = true)
    int passarParaOAnoSeguinte(@Param("ano") int ano);

    /** Fecha os vínculos de grupo ainda abertos do ano no último dia dele. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.turma_aluno
               set data_saida = :ultimoDia, status = 'INATIVO', atualizado_em = now()
             where data_saida is null
               and matricula_id in (select matricula_id from adra.matricula where ano = :ano)
            """, nativeQuery = true)
    int fecharVinculosDoAno(@Param("ano") int ano, @Param("ultimoDia") LocalDate ultimoDia);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update adra.matricula set status = 'ENCERRADA', atualizado_em = now()
             where ano = :ano and status = 'ATIVA'
            """, nativeQuery = true)
    int encerrarMatriculasDoAno(@Param("ano") int ano);

    /** Ano mais antigo, até o informado, que ainda tem matrícula ativa (candidato a encerrar). */
    @Query(value = "select min(ano) from adra.matricula where status = 'ATIVA' and ano <= :ateAno", nativeQuery = true)
    Integer menorAnoComMatriculaAtiva(@Param("ateAno") int ateAno);
}
