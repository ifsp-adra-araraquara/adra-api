package adra.ifsp.edu.br.api.domain.dto.painel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FT-05 — números do mês escolhido no Painel do Coordenador. Tudo chega
 * agregado: o front só formata e desenha, nunca percorre listas de alunos.
 *
 * @param mes        yyyy-MM
 * @param tendencia  frequência dos 6 meses até `mes`, do mais antigo ao mais recente
 * @param turmas     turmas ativas, da menor para a maior frequência (sem aula realizada por último)
 */
public record PainelMensalDTO(
        String mes,
        LocalDateTime geradoEm,
        Assistidos assistidos,
        Oficinas oficinas,
        Frequencia frequencia,
        Ocupacao ocupacao,
        Disciplina disciplina,
        List<FrequenciaMes> tendencia,
        List<Turma> turmas,
        List<FaixaEtaria> faixasEtarias
) {

    /** Ativos no fim do mês (ou hoje, se for o mês corrente) e no fim do mês anterior. */
    public record Assistidos(long ativos, long ativosMesAnterior, long entradas, long saidas) {
    }

    /**
     * Presença sobre aluno-aula esperado (mesma regra da chamada: só P conta
     * como presença; FJ é ausência). `null` quando não houve aula realizada.
     */
    public record Frequencia(
            Integer percentual,
            Integer percentualMesAnterior,
            long aulasRealizadas,
            long chamadasPendentes,
            long faltas,
            long faltasJustificadas
    ) {
    }

    /** Oficinas ativas hoje e criadas no mês (não muda com o filtro de oficina). */
    public record Oficinas(long ativas, long novas) {
    }

    public record Ocupacao(long turmasAtivas, long alunos, long vagas, Integer percentual) {
    }

    public record Disciplina(
            long ocorrencias,
            long advertencias,
            long suspensoes,
            long uniforme,
            long totalMesAnterior
    ) {
        public long total() {
            return ocorrencias + advertencias + suspensoes + uniforme;
        }
    }

    public record FrequenciaMes(String mes, Integer percentual, long aulas) {
    }

    public record Turma(
            Long turmaId,
            String nomeTurma,
            String nomeOficina,
            long alunos,
            Integer capacidade,
            long aulasRealizadas,
            long chamadasPendentes,
            Integer frequencia
    ) {
    }

    public record FaixaEtaria(String rotulo, long total) {
    }
}
