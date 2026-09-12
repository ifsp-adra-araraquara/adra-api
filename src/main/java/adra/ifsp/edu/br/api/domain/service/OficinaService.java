package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.OficinaMapper;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaSpecification;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OficinaService {

    private final OficinaRepository oficinaRepository;
    private final OficinaMapper oficinaMapper;
    private final AuditoriaService auditoriaService;

    public OficinaResponseDTO cadastrar(OficinaRequestDTO dto) {
        Oficina oficina = oficinaMapper.paraNovaEntidade(dto);
        oficina = oficinaRepository.save(oficina);

        auditoriaService.registrar(
                ModuloSistema.OFICINAS,
                "oficina",
                oficina.getOficinaId(),
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "nomeOficina", oficina.getNomeOficina(),
                        "oficineiroResponsavel", oficina.getOficineiroResponsavel(),
                        "ativo", oficina.getAtivo().toString()
                ),
                "Cadastro de oficina"
        );

        return oficinaMapper.paraDTO(oficina);
    }

    public OficinaResponseDTO buscarPorId(Long id) {
        return oficinaMapper.paraDTO(buscarEntidadePorId(id));
    }

    public List<OficinaResponseDTO> listarComFiltros(String nome, Boolean ativo) {
        Specification<Oficina> spec = OficinaSpecification.comFiltros(nome, ativo);
        return oficinaRepository.findAll(spec).stream()
                .map(OficinaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    /**
     * Suporte ao alerta NAO BLOQUEANTE de duplicidade por nome (CA de FE):
     * apenas informa se ja existe, o front decide como exibir o aviso e
     * o cadastro nao e' impedido no backend.
     */
    public boolean existeComMesmoNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return false;
        }
        return oficinaRepository.existsByNomeOficinaIgnoreCase(nome.trim());
    }

    Oficina buscarEntidadePorId(Long id) {
        return oficinaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Oficina nao encontrada: id " + id));
    }
}
