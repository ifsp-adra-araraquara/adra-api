package adra.ifsp.edu.br.api.domain.dto.disciplinar;

import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.OrigemRegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.RegistroDisciplinar;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** As relações LAZY são lidas aqui: montar dentro de transação (o DisciplinarService já é). */
public record RegistroDisciplinarResponseDTO(
        Long registroId,
        Long assistidoId,
        String nomeAssistido,
        Long motivoId,
        /** Nome do motivo, ou o texto da progressão ("Gerada pela 3ª ocorrência"). */
        String motivo,
        NivelDisciplinar nivel,
        OrigemRegistroDisciplinar origem,
        Long registroOrigemId,
        Integer sequencia,
        LocalDate dataRegistro,
        String descricao,
        Long aulaId,
        LocalDate suspensaoInicio,
        LocalDate suspensaoFim,
        StatusDocumentoDisciplinar documentoStatus,
        Long documentoArquivoId,
        ConsequenciaDisciplinar consequencia,
        boolean consequenciaPendente,
        LocalDate consequenciaResolvidaEm,
        String consequenciaObservacao,
        String consequenciaResolvidaPorNome,
        boolean arquivado,
        String arquivadoMotivo,
        OffsetDateTime arquivadoEm,
        String arquivadoPorNome,
        Long registradoPorId,
        String registradoPorNome,
        OffsetDateTime criadoEm
) {
    public static RegistroDisciplinarResponseDTO de(RegistroDisciplinar r) {
        var motivo = r.getMotivo();
        var origem = r.getRegistroOrigem();
        return new RegistroDisciplinarResponseDTO(
                r.getRegistroId(),
                r.getAssistido().getAssistidoId(),
                r.getAssistido().getNomeCompleto(),
                motivo != null ? motivo.getMotivoId() : null,
                motivo != null ? motivo.getNome() : textoProgressao(r),
                r.getNivel(),
                r.getOrigem(),
                origem != null ? origem.getRegistroId() : null,
                r.getSequencia(),
                r.getDataRegistro(),
                r.getDescricao(),
                r.getAula() != null ? r.getAula().getAulaId() : null,
                r.getSuspensaoInicio(),
                r.getSuspensaoFim(),
                r.getDocumentoStatus(),
                r.getDocumentoArquivo() != null ? r.getDocumentoArquivo().getArquivoId() : null,
                r.getConsequencia(),
                r.temConsequenciaPendente(),
                r.getConsequenciaResolvidaEm(),
                r.getConsequenciaObservacao(),
                r.getConsequenciaResolvidaPor() != null ? r.getConsequenciaResolvidaPor().getNomeCompleto() : null,
                r.isArquivado(),
                r.getArquivadoMotivo(),
                r.getArquivadoEm(),
                r.getArquivadoPor() != null ? r.getArquivadoPor().getNomeCompleto() : null,
                r.getRegistradoPor().getUsuarioId(),
                r.getRegistradoPor().getNomeCompleto(),
                r.getCriadoEm()
        );
    }

    private static String textoProgressao(RegistroDisciplinar r) {
        return switch (r.getNivel()) {
            case ADVERTENCIA -> "Gerada pela 3ª ocorrência";
            case SUSPENSAO -> "Gerada pela 3ª advertência";
            default -> "Gerado automaticamente";
        };
    }
}
