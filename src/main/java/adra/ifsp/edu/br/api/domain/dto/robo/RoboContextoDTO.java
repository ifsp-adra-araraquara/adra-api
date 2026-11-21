package adra.ifsp.edu.br.api.domain.dto.robo;

import java.util.List;

/**
 * Robozinho ADRA — o que o robô precisa para reconhecer nomes na pergunta
 * ("grupo Girassol", "oficina de teatro"): id e nome dos grupos e oficinas
 * ativos que a pessoa pode ver. Pedido uma vez ao abrir o robô.
 *
 * <p>{@code detalhe}: nos grupos, as oficinas que os atendem; nas oficinas, o
 * nome do oficineiro responsável.
 */
public record RoboContextoDTO(List<Opcao> grupos, List<Opcao> oficinas) {

    public record Opcao(Long id, String nome, String detalhe) {
    }
}
