package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TurmaAlunosRepository extends JpaRepository<TurmaAlunos, Long> {

    /** Vínculo(s) atualmente aberto(s) do assistido (dataSaida null), em qualquer turma. */
    List<TurmaAlunos> findByAssistidoAndStatusAndDataSaidaIsNull(Assistido assistido, StatusGeral status);

    /** Histórico completo de vínculos do assistido (turmas atuais e passadas) — aba "Turmas" no modal do assistido. */
    List<TurmaAlunos> findByAssistidoOrderByDataEntradaDesc(Assistido assistido);

    /** Quem está vinculado ativamente à turma HOJE — usado no "+ Vincular alunos" da tela de turmas. */
    List<TurmaAlunos> findByTurmaAndStatusAndDataSaidaIsNull(Turma turma, StatusGeral status);

    /**
     * Quem estava vinculado à turma numa data específica (a data da aula) —
     * não "quem está vinculado hoje". É o que dá pra reconstruir o roster de
     * uma aula antiga corretamente mesmo depois de alguém ter trocado de turma.
     * ATENÇÃO: isso só funciona se TurmaAlunos for de fato populada quando um
     * assistido entra/sai de uma turma (fechando dataSaida do vínculo antigo e
     * abrindo um novo). Se ainda não tiver esse código em TurmaService/
     * AssistidoService, essa query sempre vai vir vazia.
     */
    @Query("""
            select ta from TurmaAlunos ta
            where ta.turma = :turma
              and ta.status = :status
              and ta.dataEntrada <= :data
              and (ta.dataSaida is null or ta.dataSaida >= :data)
            """)
    List<TurmaAlunos> findVinculosAtivosNaData(
            @Param("turma") Turma turma,
            @Param("status") StatusGeral status,
            @Param("data") LocalDate data);

    /**
     * CA-70.1/CA-70.2: roster real de "quem pode ter chamada lançada" numa
     * aula — vínculo ativo na data (regra acima) E assistido não desligado
     * antes/na data da aula (AssistidoSpecification.elegivelParaChamada).
     * Filtra em Java de propósito: é uma regra de Assistido, não de
     * TurmaAlunos, e não vale a pena reescrever/duplicar a JPQL acima só
     * pra isso (turma pequena, filtro em memória é irrelevante em custo).
     */
    default List<TurmaAlunos> findElegiveisParaChamada(Turma turma, LocalDate data) {
        return findVinculosAtivosNaData(turma, StatusGeral.ATIVO, data).stream()
                .filter(vinculo -> AssistidoSpecification.elegivelParaChamada(vinculo.getAssistido(), data))
                .toList();
    }
}
