package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OficinaMapper {

    private final UsuarioRepository usuarioRepository;

    public Oficina paraNovaEntidade(OficinaRequestDTO dto) {
        Oficina nova = new Oficina();

        nova.setNomeOficina(dto.nomeOficina());

        if (dto.oficineiroResponsavelId() != null) {
            Usuario oficineiro = usuarioRepository.findById(dto.oficineiroResponsavelId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuário não encontrado: id " + dto.oficineiroResponsavelId()));
            nova.setOficineiroResponsavel(oficineiro);
        }

        nova.setAtivo(true);

        return nova;
    }

    public void atualizarEntidade(Oficina oficina, OficinaRequestDTO dto) {
        oficina.setNomeOficina(dto.nomeOficina());

        if (dto.oficineiroResponsavelId() != null) {
            Usuario oficineiro = usuarioRepository.findById(dto.oficineiroResponsavelId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuário não encontrado: id " + dto.oficineiroResponsavelId()));
            oficina.setOficineiroResponsavel(oficineiro);
        }
    }

    public OficinaResponseDTO paraDTO(Oficina oficina) {
        return new OficinaResponseDTO(
                oficina.getOficinaId(),
                oficina.getNomeOficina(),
                oficina.getOficineiroResponsavel() != null ? oficina.getOficineiroResponsavel().getUsuarioId() : null,
                oficina.getAtivo()
        );
    }
}
