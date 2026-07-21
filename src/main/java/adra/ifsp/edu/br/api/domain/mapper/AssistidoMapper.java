package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Assistido;
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
                entidade.getTotalOcorrenciasAtivas(),
                entidade.getTotalAdvertenciasAtivas(),
                entidade.getTotalSuspensoes(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm()
        );
    }
}
