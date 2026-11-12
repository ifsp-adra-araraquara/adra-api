package adra.ifsp.edu.br.api.domain.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import adra.ifsp.edu.br.api.domain.dto.lista.AtendidoListaEventoDTO;
import lombok.RequiredArgsConstructor;

/** Atendidos ativos hoje nos grupos escolhidos, numa consulta só (lista de evento/passeio). */
@Repository
@RequiredArgsConstructor
public class ListaEventoRepository {

    private final JdbcClient jdbc;

    /**
     * Vínculo aberto na data (quem só começa no próximo ano fica de fora) e atendido ativo.
     * Um atendido aparece uma vez só, mesmo se estiver em dois grupos marcados.
     */
    public List<AtendidoListaEventoDTO> atendidos(Collection<Long> turmaIds, LocalDate dia, boolean comResponsavel) {
        return jdbc.sql("""
                        SELECT DISTINCT ON (a.assistido_id)
                               a.assistido_id, a.nome_completo, a.data_nascimento, t.turma_id, t.nome_turma,
                               resp.nome_completo AS responsavel_nome, resp.telefone AS responsavel_telefone
                          FROM adra.turma_aluno ta
                          JOIN adra.assistido a ON a.assistido_id = ta.assistido_id AND a.status = 'ATIVO'
                          JOIN adra.turma t ON t.turma_id = ta.turma_id
                          LEFT JOIN LATERAL (
                                SELECT r.nome_completo, r.telefone
                                  FROM adra.assistido_responsavel ar
                                  JOIN adra.responsavel r ON r.responsavel_id = ar.responsavel_id
                                 WHERE ar.assistido_id = a.assistido_id
                                 ORDER BY ar.responsavel_principal DESC, r.responsavel_id
                                 LIMIT 1
                          ) resp ON :comResponsavel
                         WHERE ta.turma_id IN (:turmaIds)
                           AND ta.status = 'ATIVO'
                           AND ta.data_entrada <= :dia
                           AND (ta.data_saida IS NULL OR ta.data_saida >= :dia)
                         ORDER BY a.assistido_id, ta.data_entrada DESC
                        """)
                .param("turmaIds", turmaIds)
                .param("dia", dia)
                .param("comResponsavel", comResponsavel)
                .query((rs, i) -> new AtendidoListaEventoDTO(
                        rs.getLong("assistido_id"),
                        rs.getString("nome_completo"),
                        rs.getObject("data_nascimento", LocalDate.class),
                        rs.getLong("turma_id"),
                        rs.getString("nome_turma"),
                        rs.getString("responsavel_nome"),
                        rs.getString("responsavel_telefone")))
                .list();
    }
}
