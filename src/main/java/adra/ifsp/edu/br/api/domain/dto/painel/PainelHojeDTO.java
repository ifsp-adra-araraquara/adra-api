package adra.ifsp.edu.br.api.domain.dto.painel;

import adra.ifsp.edu.br.api.domain.dto.disciplinar.PendenciasDisciplinaresDTO;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * FT-05 — bloco "Precisa de atenção hoje". Não depende do mês escolhido no
 * painel, por isso é um endpoint separado: trocar de mês não refaz estas
 * consultas. Cada lista vem cortada (LIMITE) com o total ao lado.
 */
public record PainelHojeDTO(
        LocalDate hoje,
        Lista<AlunoFaltas> faltasSeguidas,
        Lista<ChamadaPendente> chamadasPendentes,
        PendenciasDisciplinaresDTO disciplina,
        Lista<UmPasso> umPassoDaProgressao,
        List<Aniversariante> aniversariantes,
        List<Atividade> atividades
) {

    /** Primeiros itens de uma lista + quantos existem ao todo. */
    public record Lista<T>(List<T> itens, long total) {
    }

    public record AlunoFaltas(Long assistidoId, String nome, String turma, long faltas, LocalDate ultimaAula) {
    }

    public record ChamadaPendente(Long aulaId, LocalDate data, String horario, String turma) {
    }

    /** Assistido com 2 registros do nível (de 3): o próximo gera a consequência. */
    public record UmPasso(Long assistidoId, String nome, String nivel, long registros) {
    }

    /**
     * @param tipo         CHAMADA (referenciaId = aula), DISCIPLINAR (= registro) ou ASSISTIDO
     * @param titulo       turma da chamada ou nome do assistido
     * @param detalhe      data da aula (dd/MM) ou nível disciplinar
     */
    public record Atividade(String tipo, Long referenciaId, Long assistidoId, String titulo, String detalhe,
                            Instant quando) {
    }

    public record Aniversariante(Long assistidoId, String nome, String turma, LocalDate dia, int idade) {
    }
}
