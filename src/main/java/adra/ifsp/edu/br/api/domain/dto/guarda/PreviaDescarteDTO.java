package adra.ifsp.edu.br.api.domain.dto.guarda;

import java.time.LocalDate;
import java.util.List;

/**
 * O que sai no descarte pelo prazo de guarda (5 anos por matrícula), antes de confirmar.
 * Só números e nomes dos atendidos que saem por inteiro — para a equipe exportar antes.
 */
public record PreviaDescarteDTO(
        /** Matrículas criadas antes deste dia já passaram dos 5 anos. */
        LocalDate limite,
        int matriculas,
        /** Anos com matrícula vencida (para exportar a lista de cada um antes). */
        List<Integer> anos,
        int atendidosRemovidos,
        List<AtendidoRemovidoDTO> quemSaiPorInteiro,
        int responsaveis,
        int preCadastros,
        int registrosDisciplinares,
        int arquivos,
        long bytes
) {
    public record AtendidoRemovidoDTO(Long assistidoId, String nomeCompleto, Integer ultimoAno) {
    }

    public boolean vazio() {
        return matriculas == 0 && preCadastros == 0;
    }
}
