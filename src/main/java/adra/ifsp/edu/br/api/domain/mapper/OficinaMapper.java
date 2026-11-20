package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

        nova.setLocal(textoOuNulo(dto.local()));
        nova.setDiasDaSemana(diasOrdenados(dto.diasDaSemana()));
        nova.setAtivo(true);

        return nova;
    }

    public void atualizarEntidade(Oficina oficina, OficinaRequestDTO dto) {
        oficina.setNomeOficina(dto.nomeOficina());
        oficina.setLocal(textoOuNulo(dto.local()));
        if (dto.diasDaSemana() != null) {
            oficina.setDiasDaSemana(diasOrdenados(dto.diasDaSemana()));
        }

        if (dto.oficineiroResponsavelId() != null) {
            Usuario oficineiro = usuarioRepository.findById(dto.oficineiroResponsavelId())
                    .orElseThrow(() -> new EntidadeNaoEncontradaException("Usuário não encontrado: id " + dto.oficineiroResponsavelId()));
            oficina.setOficineiroResponsavel(oficineiro);
        }
    }

    public OficinaResponseDTO paraDTO(Oficina oficina, List<Long> turmaIds) {
        return new OficinaResponseDTO(
                oficina.getOficinaId(),
                oficina.getNomeOficina(),
                oficina.getOficineiroResponsavel() != null ? oficina.getOficineiroResponsavel().getUsuarioId() : null,
                oficina.getLocal(),
                oficina.getAtivo(),
                turmaIds,
                diasOrdenados(oficina.getDiasDaSemana())
        );
    }

    /** Campo em branco vira null, para "sem local" ter uma forma só no banco. */
    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static List<DayOfWeek> diasOrdenados(List<DayOfWeek> dias) {
        if (dias == null) {
            return new ArrayList<>();
        }
        return dias.stream().filter(Objects::nonNull).distinct().sorted().collect(Collectors.toCollection(ArrayList::new));
    }
}
