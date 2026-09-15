package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import org.springframework.stereotype.Component;

@Component
public class OficinaMapper {

    private OficinaMapper() {
        // classe utilitária, não deve ser instanciada
    }

    public static Oficina paraNovaEntidade(OficinaRequestDTO dto) {
        Oficina nova = new Oficina();

        nova.setNomeOficina(dto.nomeOficina());
        nova.setOficineiroResponsavel(dto.oficineiroResponsavel());
        nova.setAtivo(true);

        return nova;
    }

    public static OficinaResponseDTO paraDTO(Oficina oficina) {
        return new OficinaResponseDTO(
                oficina.getOficinaId(),
                oficina.getNomeOficina(),
                oficina.getOficineiroResponsavel(),
                oficina.getAtivo()
        );
    }
}
