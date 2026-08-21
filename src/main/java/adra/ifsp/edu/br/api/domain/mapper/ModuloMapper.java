package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.modulo.ModuloDTO;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Modulo;
import org.springframework.stereotype.Component;

@Component
public class ModuloMapper {
    public Modulo paraModulo(ModuloDTO moduloDTO) {
        return Modulo.builder()
                .codigo(moduloDTO.codigo())
                .nomeExibicao(moduloDTO.nomeExibicao())
                .rota(moduloDTO.rota())
                .build();
    }

    public ModuloDTO paraDTO(Modulo modulo) {
        return new ModuloDTO(
                modulo.getCodigo(),
                modulo.getNomeExibicao(),
                modulo.getRota()
        );
    }
}