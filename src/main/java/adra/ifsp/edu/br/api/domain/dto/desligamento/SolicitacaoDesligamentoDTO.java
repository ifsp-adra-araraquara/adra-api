package adra.ifsp.edu.br.api.domain.dto.desligamento;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import adra.ifsp.edu.br.api.domain.enums.StatusSolicitacaoDesligamento;
import adra.ifsp.edu.br.api.domain.model.SolicitacaoDesligamento;

public record SolicitacaoDesligamentoDTO(
        Long solicitacaoId,
        Long assistidoId,
        String nomeAtendido,
        String nomeTurma,
        LocalDate dataSaida,
        String motivo,
        StatusSolicitacaoDesligamento status,
        String solicitadoPorNome,
        OffsetDateTime criadoEm,
        String resposta,
        String decididoPorNome,
        OffsetDateTime decididoEm
) {
    public static SolicitacaoDesligamentoDTO de(SolicitacaoDesligamento s) {
        return new SolicitacaoDesligamentoDTO(
                s.getSolicitacaoId(),
                s.getAssistido().getAssistidoId(),
                s.getAssistido().getNomeCompleto(),
                s.getAssistido().getTurma() != null ? s.getAssistido().getTurma().getNomeTurma() : null,
                s.getDataSaida(),
                s.getMotivo(),
                s.getStatus(),
                s.getSolicitadoPor().getNomeCompleto(),
                s.getCriadoEm(),
                s.getResposta(),
                s.getDecididoPor() != null ? s.getDecididoPor().getNomeCompleto() : null,
                s.getDecididoEm());
    }
}
