package adra.ifsp.edu.br.api.domain.dto.matricula;

import java.time.LocalDate;
import java.util.List;

import adra.ifsp.edu.br.api.domain.enums.StatusMatricula;

/**
 * Lista de atendidos de um ano letivo, para imprimir/salvar em PDF e exportar
 * em planilha como histórico. Gerada na hora (nada fica guardado).
 * Só o necessário para o histórico: sem CPF, endereço ou contatos.
 */
public record ListaAnualDTO(
        int ano,
        boolean encerrado,
        /** Frequência considerada até este dia (hoje, no ano em curso; 31/12 nos anteriores). */
        LocalDate frequenciaAte,
        int totalAtivas,
        int totalEncerradas,
        int totalDesligadas,
        List<LinhaDTO> atendidos
) {
    public record LinhaDTO(
            Long assistidoId,
            String nomeCompleto,
            LocalDate dataNascimento,
            StatusMatricula statusMatricula,
            String grupo,
            LocalDate entrada,
            LocalDate saida,
            int totalChamadas,
            int presencas,
            int faltas,
            int faltasJustificadas,
            /** % de presença (null sem chamada no ano). Falta justificada não conta como presença. */
            Integer frequenciaPercentual
    ) {
    }
}
