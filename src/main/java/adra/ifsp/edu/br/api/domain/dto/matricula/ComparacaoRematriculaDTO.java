package adra.ifsp.edu.br.api.domain.dto.matricula;

import java.util.List;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;

/**
 * Tela de validação da rematrícula: o que mudou entre a ficha e o formulário.
 * Só leitura — a ficha não é alterada; a equipe edita à mão se quiser.
 */
public record ComparacaoRematriculaDTO(
        Long assistidoId,
        String nomeAssistido,
        StatusGeral statusAssistido,
        /** Ano da inscrição (o da matrícula que será aberta). */
        int ano,
        boolean jaMatriculadoNoAno,
        /** Grupo atual do atendido, para sugerir na rematrícula (null se não tem). */
        Long turmaAtualId,
        String nomeTurmaAtual,
        List<CampoComparadoDTO> campos
) {
}
