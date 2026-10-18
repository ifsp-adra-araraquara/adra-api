package adra.ifsp.edu.br.api.domain.dto.presenca;

import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.FaltaJustificada;
import adra.ifsp.edu.br.api.domain.model.Presenca;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PresencaResponseDTO(
        Long presencaId,
        Long aulaId,
        Long assistidoId,
        // CA-66.1: front monta a lista de alunos da chamada só com este DTO
        // (GET /api/chamadas/aula/{id}), sem precisar de um segundo request a
        // /api/assistidos — evita ter duas fontes de roster desalinhadas.
        String nomeCompleto,
        // Termo "pode ir embora sozinho" do assistido — aparece em toda lista de presença.
        boolean podeIrEmboraSozinho,
        // Para o card de aniversário na chamada.
        LocalDate dataNascimento,
        StatusPresenca statusPresenca,
        MotivoFalta motivoFalta,
        String observacao,
        // US-68 (CA-68.1 e CA-68.2): rastreabilidade de autoria de quem lançou ou alterou
        Long criadoPorId,
        String criadoPorNome,
        Long atualizadoPorId,
        String atualizadoPorNome,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        // Últimos dias (mais recente primeiro, janela fixa pequena — ver
        // PresencaService) do mesmo aluno na turma, só preenchido na resposta
        // de GET /api/chamadas/aula/{id} (buscarPorAula). Alimenta a "faixa de
        // dias" no roster da chamada — o limiar de alerta de faltas seguidas é
        // calculado no front a partir desta lista, não duplicado aqui. Vazia
        // nos outros usos deste DTO (registrarChamada, atualizarPresenca).
        List<PresencaHistoricoDTO> historicoRecente,
        // Resumo de frequência do aluno — só em buscarPorAula (null nos outros usos):
        // % de presença nas aulas realizadas, quantas aulas realizadas e
        // quantas a turma tem no total (não canceladas).
        Integer frequenciaPercentual,
        Integer aulasRealizadas,
        Integer aulasPrevistas,
        // Responsáveis marcados como "autorizado a retirar" — só em buscarPorAula.
        List<AutorizadoRetiradaDTO> autorizadosRetirada
) {

    /** Presenca sem falta justificada associada (status FALTA). */
    public static PresencaResponseDTO fromEntity(Presenca presenca) {
        return fromEntity(presenca, null);
    }

    /**
     * Modelo esparso: PRESENTE nunca vira linha na tabela presenca, então não
     * existe uma Presenca pra montar esse DTO — é só a ausência de registro de
     * falta pra esse (aula, assistido). presencaId/criadoEm/atualizadoEm ficam null
     * de propósito, pra deixar claro pro consumidor que isso é inferido, não persistido.
     */
    public static PresencaResponseDTO presente(Long aulaId, Assistido assistido) {
        return new PresencaResponseDTO(
                null, aulaId, assistido.getAssistidoId(), assistido.getNomeCompleto(),
                assistido.isPodeIrEmboraSozinho(), assistido.getDataNascimento(), StatusPresenca.PRESENTE, null, null,
                null, null, null, null, null, null, List.of(), null, null, null, List.of()
        );
    }

    public static PresencaResponseDTO fromEntity(Presenca presenca, FaltaJustificada faltaJustificada) {
        return new PresencaResponseDTO(
                presenca.getPresencaId(),
                presenca.getAula() != null ? presenca.getAula().getAulaId() : null,
                presenca.getAssistido() != null ? presenca.getAssistido().getAssistidoId() : null,
                presenca.getAssistido() != null ? presenca.getAssistido().getNomeCompleto() : null,
                presenca.getAssistido() != null && presenca.getAssistido().isPodeIrEmboraSozinho(),
                presenca.getAssistido() != null ? presenca.getAssistido().getDataNascimento() : null,
                presenca.getStatusPresenca(),
                faltaJustificada != null ? faltaJustificada.getMotivoFalta() : null,
                faltaJustificada != null ? faltaJustificada.getObservacao() : null,
                presenca.getCriadoPor() != null ? presenca.getCriadoPor().getUsuarioId() : null,
                presenca.getCriadoPor() != null ? presenca.getCriadoPor().getNomeCompleto() : null,
                presenca.getAtualizadoPor() != null ? presenca.getAtualizadoPor().getUsuarioId() : null,
                presenca.getAtualizadoPor() != null ? presenca.getAtualizadoPor().getNomeCompleto() : null,
                presenca.getCriadoEm(),
                presenca.getAtualizadoEm(),
                List.of(),
                null, null, null,
                List.of()
        );
    }

    /** Mesmo DTO com `historicoRecente` preenchido — usado só por `buscarPorAula`. */
    public PresencaResponseDTO comHistoricoRecente(List<PresencaHistoricoDTO> historicoRecente) {
        return new PresencaResponseDTO(
                presencaId, aulaId, assistidoId, nomeCompleto, podeIrEmboraSozinho, dataNascimento, statusPresenca, motivoFalta, observacao,
                criadoPorId, criadoPorNome, atualizadoPorId, atualizadoPorNome, criadoEm, atualizadoEm,
                historicoRecente, frequenciaPercentual, aulasRealizadas, aulasPrevistas, autorizadosRetirada
        );
    }

    /** Mesmo DTO com o resumo de frequência — usado só por `buscarPorAula`. */
    public PresencaResponseDTO comFrequencia(Integer percentual, Integer realizadas, Integer previstas) {
        return new PresencaResponseDTO(
                presencaId, aulaId, assistidoId, nomeCompleto, podeIrEmboraSozinho, dataNascimento, statusPresenca, motivoFalta, observacao,
                criadoPorId, criadoPorNome, atualizadoPorId, atualizadoPorNome, criadoEm, atualizadoEm,
                historicoRecente, percentual, realizadas, previstas, autorizadosRetirada
        );
    }

    /** Mesmo DTO com quem pode buscar o aluno — usado só por `buscarPorAula`. */
    public PresencaResponseDTO comAutorizadosRetirada(List<AutorizadoRetiradaDTO> autorizados) {
        return new PresencaResponseDTO(
                presencaId, aulaId, assistidoId, nomeCompleto, podeIrEmboraSozinho, dataNascimento, statusPresenca, motivoFalta, observacao,
                criadoPorId, criadoPorNome, atualizadoPorId, atualizadoPorNome, criadoEm, atualizadoEm,
                historicoRecente, frequenciaPercentual, aulasRealizadas, aulasPrevistas, autorizados
        );
    }
}
