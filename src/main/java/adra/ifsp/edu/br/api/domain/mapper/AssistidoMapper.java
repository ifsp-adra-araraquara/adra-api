package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.service.IndicadorFrequencia;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AssistidoMapper {

    public Assistido paraNovaEntidade(AssistidoRequestDTO dto) {
        return Assistido.builder()
                .nomeCompleto(dto.nomeCompleto())
                .dataNascimento(dto.dataNascimento())
                .cpf(dto.cpf())
                .dataEntrada(dto.dataEntrada() != null ? dto.dataEntrada() : LocalDate.now())
                .necessidadesEspecificas(dto.necessidadesEspecificas())
                .observacoes(dto.observacoes())
                .build();
    }

    /** Atualiza apenas os campos cadastrais editaveis por este card (nao mexe em status/contadores). */
    public void atualizarEntidade(Assistido entidade, AssistidoRequestDTO dto) {
        entidade.setNomeCompleto(dto.nomeCompleto());
        entidade.setDataNascimento(dto.dataNascimento());
        entidade.setCpf(dto.cpf());
        if (dto.dataEntrada() != null) {
            entidade.setDataEntrada(dto.dataEntrada());
        }
        entidade.setNecessidadesEspecificas(dto.necessidadesEspecificas());
        entidade.setObservacoes(dto.observacoes());
    }

    public AssistidoResponseDTO paraDTO(Assistido entidade) {
        return paraDTO(entidade, null, null);
    }

    /**
     * Linha da listagem: além do cadastro, o responsável principal (ou
     * {@code null}) e os indicadores de frequência (ou {@code null}).
     */
    public AssistidoResponseDTO paraDTO(Assistido entidade, AssistidoResponsavel responsavel, IndicadorFrequencia frequencia) {
        Long turmaId = entidade.getTurma() != null ? entidade.getTurma().getTurmaId() : null;
        String nomeTurma = entidade.getTurma() != null ? entidade.getTurma().getNomeTurma() : null;

        return new AssistidoResponseDTO(
                entidade.getAssistidoId(),
                entidade.getNomeCompleto(),
                entidade.getDataNascimento(),
                entidade.getCpf(),
                entidade.getDataEntrada(),
                entidade.getDataSaida(),
                entidade.getMotivoSaida(),
                entidade.getNecessidadesEspecificas(),
                entidade.getObservacoes(),
                entidade.getStatus(),
                turmaId,
                nomeTurma,
                entidade.getTotalOcorrenciasAtivas(),
                entidade.getTotalAdvertenciasAtivas(),
                entidade.getTotalSuspensoes(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm(),
                responsavel != null ? responsavel.getResponsavel().getNomeCompleto() : null,
                responsavel != null ? responsavel.getParentesco() : null,
                responsavel != null ? responsavel.getResponsavel().getTelefone() : null,
                frequencia != null ? frequencia.percentual() : null,
                frequencia != null ? frequencia.totalAulas() : null,
                frequencia != null ? frequencia.faltasConsecutivas() : null,
                frequencia != null ? frequencia.emAcompanhamento() : null
        );
    }
}
