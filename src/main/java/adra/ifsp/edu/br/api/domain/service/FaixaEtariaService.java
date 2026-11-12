package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.faixaetaria.FaixaEtariaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.faixaetaria.FaixaEtariaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.FaixaEtaria;
import adra.ifsp.edu.br.api.domain.repository.FaixaEtariaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class FaixaEtariaService {

    private final FaixaEtariaRepository repository;
    private final AuditoriaService auditoriaService;

    @Cacheable(CacheConfig.REFERENCIAS)
    @Transactional(readOnly = true)
    public List<FaixaEtariaResponseDTO> listar(boolean incluirInativas) {
        List<FaixaEtaria> faixas = incluirInativas
                ? repository.findAllByOrderByIdadeMinimaAscIdadeMaximaAsc()
                : repository.findByAtivoTrueOrderByIdadeMinimaAscIdadeMaximaAsc();
        return faixas.stream().map(FaixaEtariaResponseDTO::de).toList();
    }

    @CacheEvict(cacheNames = {CacheConfig.REFERENCIAS, CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public FaixaEtariaResponseDTO criar(FaixaEtariaRequestDTO dto) {
        validarIntervalo(dto);
        if (repository.existsByIdadeMinimaAndIdadeMaxima(dto.idadeMinima(), dto.idadeMaxima())) {
            throw intervaloDuplicado(dto);
        }

        FaixaEtaria faixa = repository.save(FaixaEtaria.builder()
                .idadeMinima(dto.idadeMinima())
                .idadeMaxima(dto.idadeMaxima())
                .ativo(dto.ativo() == null || dto.ativo())
                .build());

        auditoriaService.registrar(ModuloSistema.TURMAS, "faixa_etaria", faixa.getFaixaEtariaId(),
                AcaoSistema.CRIAR, null, valores(faixa), "Cadastro de faixa etária");
        return FaixaEtariaResponseDTO.de(faixa);
    }

    /** Alterar o intervalo muda o rótulo de todos os grupos que usam a faixa. */
    @CacheEvict(cacheNames = {CacheConfig.REFERENCIAS, CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public FaixaEtariaResponseDTO atualizar(Long faixaEtariaId, FaixaEtariaRequestDTO dto) {
        FaixaEtaria faixa = buscar(faixaEtariaId);
        validarIntervalo(dto);
        if (repository.existsByIdadeMinimaAndIdadeMaximaAndFaixaEtariaIdNot(
                dto.idadeMinima(), dto.idadeMaxima(), faixaEtariaId)) {
            throw intervaloDuplicado(dto);
        }

        Map<String, Object> anterior = valores(faixa);
        faixa.setIdadeMinima(dto.idadeMinima());
        faixa.setIdadeMaxima(dto.idadeMaxima());
        if (dto.ativo() != null) {
            faixa.setAtivo(dto.ativo());
        }

        auditoriaService.registrar(ModuloSistema.TURMAS, "faixa_etaria", faixaEtariaId, AcaoSistema.EDITAR,
                anterior, valores(faixa), "Alteração de faixa etária");
        return FaixaEtariaResponseDTO.de(faixa);
    }

    /** Faixa para gravar no grupo: inativa só é aceita se o grupo já estiver nela. */
    public FaixaEtaria buscarParaGrupo(Long faixaEtariaId, FaixaEtaria atualDoGrupo) {
        if (faixaEtariaId == null) {
            return null;
        }
        FaixaEtaria faixa = buscar(faixaEtariaId);
        boolean jaEraDoGrupo = atualDoGrupo != null && faixaEtariaId.equals(atualDoGrupo.getFaixaEtariaId());
        if (!faixa.isAtivo() && !jaEraDoGrupo) {
            throw new RegraNegocioException("A faixa etária " + faixa.getRotulo() + " está inativa.");
        }
        return faixa;
    }

    private FaixaEtaria buscar(Long faixaEtariaId) {
        return repository.findById(faixaEtariaId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Faixa etária não encontrada: id " + faixaEtariaId));
    }

    private static void validarIntervalo(FaixaEtariaRequestDTO dto) {
        if (dto.idadeMinima() > dto.idadeMaxima()) {
            throw new RegraNegocioException("A idade mínima não pode ser maior que a máxima.");
        }
    }

    private static RegraNegocioException intervaloDuplicado(FaixaEtariaRequestDTO dto) {
        return new RegraNegocioException(
                "A faixa etária " + FaixaEtaria.rotulo(dto.idadeMinima(), dto.idadeMaxima()) + " já existe.");
    }

    private static Map<String, Object> valores(FaixaEtaria faixa) {
        return Map.of("idadeMinima", faixa.getIdadeMinima(), "idadeMaxima", faixa.getIdadeMaxima(),
                "ativo", faixa.isAtivo());
    }
}
