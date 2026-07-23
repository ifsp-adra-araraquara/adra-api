package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.ResponsavelMapper;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ResponsavelService {

    private final ResponsavelRepository responsavelRepository;
    private final ResponsavelMapper responsavelMapper;
    private final AuditoriaService auditoriaService;

    public ResponsavelResponseDTO cadastrar(ResponsavelRequestDTO dto) {
        Responsavel responsavel = responsavelMapper.paraNovaEntidade(dto);
        responsavel = responsavelRepository.save(responsavel);

        auditoriaService.registrar(
                ModuloSistema.RESPONSAVEIS,
                "responsavel",
                responsavel.getResponsavelId(),
                AcaoSistema.CRIAR,
                null,
                Map.of("nomeCompleto", responsavel.getNomeCompleto()),
                "Cadastro de responsavel"
        );

        return responsavelMapper.paraDTO(responsavel);
    }

    @Transactional(readOnly = true)
    public ResponsavelResponseDTO buscarPorId(Long id) {
        return responsavelMapper.paraDTO(buscarEntidadePorId(id));
    }

    @Transactional(readOnly = true)
    public List<ResponsavelResponseDTO> listar() {
        return responsavelRepository.findAll().stream()
                .map(responsavelMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public ResponsavelResponseDTO atualizar(Long id, ResponsavelRequestDTO dto) {
        Responsavel responsavel = buscarEntidadePorId(id);

        Map<String, Object> valorAnterior = Map.of(
                "nomeCompleto", responsavel.getNomeCompleto(),
                "telefone", String.valueOf(responsavel.getTelefone())
        );

        responsavelMapper.atualizarEntidade(responsavel, dto);
        responsavel = responsavelRepository.save(responsavel);

        auditoriaService.registrar(
                ModuloSistema.RESPONSAVEIS,
                "responsavel",
                responsavel.getResponsavelId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of("nomeCompleto", responsavel.getNomeCompleto(), "telefone", String.valueOf(responsavel.getTelefone())),
                "Atualizacao de dados cadastrais"
        );

        return responsavelMapper.paraDTO(responsavel);
    }

    Responsavel buscarEntidadePorId(Long id) {
        return responsavelRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Responsavel nao encontrado: id " + id));
    }
}
