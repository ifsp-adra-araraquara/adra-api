package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresencaMapper {

    private final AulaRepository aulaRepository;
    private final AssistidoRepository assistidoRepository;

    public Presenca paraNovaEntidade(PresencaRequestDTO dto) {
        Presenca presenca = new Presenca();
        atualizarEntidade(presenca, dto);
        return presenca;
    }

    public void atualizarEntidade(Presenca presenca, PresencaRequestDTO dto) {
        if (dto.aulaId() != null) {
            Aula aula = aulaRepository.findById(dto.aulaId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + dto.aulaId()));
            presenca.setAula(aula);
        }
        if (dto.assistidoId() != null) {
            Assistido assistido = assistidoRepository.findById(dto.assistidoId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + dto.assistidoId()));
            presenca.setAssistido(assistido);
        }
        presenca.setStatusPresenca(dto.statusPresenca());
        presenca.setJustificativaFalta(dto.justificativaFalta());
        presenca.setObservacaoDoDia(dto.observacaoDoDia());
    }

    public PresencaResponseDTO paraDTO(Presenca presenca) {
        return PresencaResponseDTO.fromEntity(presenca);
    }
}
