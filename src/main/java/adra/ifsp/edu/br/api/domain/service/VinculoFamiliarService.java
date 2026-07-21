package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.VinculoFamiliarMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavelId;
import adra.ifsp.edu.br.api.domain.model.Responsavel;
import adra.ifsp.edu.br.api.domain.repository.AssistidoResponsavelRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VinculoFamiliarService {

    private final AssistidoResponsavelRepository vinculoRepository;
    private final VinculoFamiliarMapper vinculoMapper;
    private final AuditoriaService auditoriaService;

    // Reaproveita a busca/validacao de existencia dos outros dois services -
    // evita duplicar EntidadeNaoEncontradaException aqui.
    private final AssistidoService assistidoService;
    private final ResponsavelService responsavelService;

    public VinculoFamiliarResponseDTO vincular(Long assistidoId, VinculoFamiliarRequestDTO dto) {
        Assistido assistido = assistidoService.buscarEntidadePorId(assistidoId);
        Responsavel responsavel = responsavelService.buscarEntidadePorId(dto.responsavelId());

        vinculoRepository.findByIdAssistidoIdAndIdResponsavelId(assistidoId, dto.responsavelId())
                .ifPresent(v -> {
                    throw new RegraNegocioException(
                            "Este responsavel ja esta vinculado a este assistido. Use a edicao do vinculo.");
                });

        if (dto.responsavelPrincipal()) {
            validarUnicoResponsavelPrincipal(assistidoId, dto.responsavelId());
        }

        AssistidoResponsavel vinculo = AssistidoResponsavel.builder()
                .id(new AssistidoResponsavelId(assistidoId, dto.responsavelId()))
                .assistido(assistido)
                .responsavel(responsavel)
                .parentesco(dto.parentesco())
                .responsavelPrincipal(dto.responsavelPrincipal())
                .contatoEmergencia(dto.contatoEmergencia())
                .autorizadoRetirada(dto.autorizadoRetirada())
                .observacoes(dto.observacoes())
                .build();

        vinculo = vinculoRepository.save(vinculo);

        auditoriaService.registrar(
                ModuloSistema.ASSISTIDOS,
                "assistido_responsavel",
                assistidoId,
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "responsavelId", dto.responsavelId(),
                        "parentesco", String.valueOf(dto.parentesco()),
                        "responsavelPrincipal", dto.responsavelPrincipal()
                ),
                "Vinculo familiar criado (assistido " + assistidoId + " <-> responsavel " + dto.responsavelId() + ")"
        );

        return vinculoMapper.paraDTO(vinculo);
    }

    @Transactional(readOnly = true)
    public List<VinculoFamiliarResponseDTO> listarPorAssistido(Long assistidoId) {
        assistidoService.buscarEntidadePorId(assistidoId); // valida que o assistido existe
        return vinculoRepository.findByIdAssistidoId(assistidoId).stream()
                .map(vinculoMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public VinculoFamiliarResponseDTO atualizar(Long assistidoId, Long responsavelId, VinculoFamiliarRequestDTO dto) {
        AssistidoResponsavel vinculo = buscarVinculo(assistidoId, responsavelId);

        if (dto.responsavelPrincipal()) {
            validarUnicoResponsavelPrincipal(assistidoId, responsavelId);
        }

        Map<String, Object> valorAnterior = Map.of(
                "parentesco", String.valueOf(vinculo.getParentesco()),
                "responsavelPrincipal", vinculo.isResponsavelPrincipal()
        );

        vinculo.setParentesco(dto.parentesco());
        vinculo.setResponsavelPrincipal(dto.responsavelPrincipal());
        vinculo.setContatoEmergencia(dto.contatoEmergencia());
        vinculo.setAutorizadoRetirada(dto.autorizadoRetirada());
        vinculo.setObservacoes(dto.observacoes());
        vinculo = vinculoRepository.save(vinculo);

        auditoriaService.registrar(
                ModuloSistema.ASSISTIDOS,
                "assistido_responsavel",
                assistidoId,
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of("parentesco", String.valueOf(dto.parentesco()), "responsavelPrincipal", dto.responsavelPrincipal()),
                "Vinculo familiar atualizado (assistido " + assistidoId + " <-> responsavel " + responsavelId + ")"
        );

        return vinculoMapper.paraDTO(vinculo);
    }

    /** Remove apenas o vinculo - NAO apaga o cadastro de assistido nem de responsavel (regra explicita do card). */
    public void desvincular(Long assistidoId, Long responsavelId) {
        AssistidoResponsavel vinculo = buscarVinculo(assistidoId, responsavelId);
        vinculoRepository.delete(vinculo);

        auditoriaService.registrar(
                ModuloSistema.ASSISTIDOS,
                "assistido_responsavel",
                assistidoId,
                AcaoSistema.EXCLUIR,
                Map.of("responsavelId", responsavelId),
                null,
                "Vinculo familiar removido (assistido " + assistidoId + " <-> responsavel " + responsavelId + ")"
        );
    }

    private AssistidoResponsavel buscarVinculo(Long assistidoId, Long responsavelId) {
        return vinculoRepository.findByIdAssistidoIdAndIdResponsavelId(assistidoId, responsavelId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException(
                        "Vinculo nao encontrado entre assistido " + assistidoId + " e responsavel " + responsavelId));
    }

    /**
     * Validacao preventiva da regra "no maximo 1 responsavel principal por
     * assistido" (indice unico parcial assistido_um_principal_uk no banco e'
     * a garantia final, isto aqui e' so para dar um erro de negocio legivel
     * antes de chegar la).
     */
    private void validarUnicoResponsavelPrincipal(Long assistidoId, Long responsavelId) {
        boolean jaExisteOutroPrincipal = vinculoRepository
                .existsByIdAssistidoIdAndResponsavelPrincipalTrueAndIdResponsavelIdNot(assistidoId, responsavelId);

        if (jaExisteOutroPrincipal) {
            throw new RegraNegocioException(
                    "Ja existe um responsavel principal para este assistido. Remova a marcacao atual antes de definir outro.");
        }
    }
}
