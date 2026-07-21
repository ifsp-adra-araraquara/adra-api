package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarResponseDTO;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import org.springframework.stereotype.Component;

@Component
public class VinculoFamiliarMapper {

    public VinculoFamiliarResponseDTO paraDTO(AssistidoResponsavel vinculo) {
        return new VinculoFamiliarResponseDTO(
                vinculo.getId().getAssistidoId(),
                vinculo.getId().getResponsavelId(),
                vinculo.getResponsavel().getNomeCompleto(),
                vinculo.getResponsavel().getTelefone(),
                vinculo.getResponsavel().getEmail(),
                vinculo.getParentesco(),
                vinculo.isResponsavelPrincipal(),
                vinculo.isContatoEmergencia(),
                vinculo.isAutorizadoRetirada(),
                vinculo.getObservacoes(),
                vinculo.getCriadoEm()
        );
    }
}
