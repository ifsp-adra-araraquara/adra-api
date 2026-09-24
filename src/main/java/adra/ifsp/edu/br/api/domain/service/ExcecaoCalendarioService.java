package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.excecao.ExcecaoCalendarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.excecao.ExcecaoCalendarioResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.ExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.repository.ExcecaoCalendarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ExcecaoCalendarioService {

    private final ExcecaoCalendarioRepository excecaoCalendarioRepository;
    private final AuditoriaService auditoriaService;

    public ExcecaoCalendarioResponseDTO cadastrar(ExcecaoCalendarioRequestDTO dto) {
        if (excecaoCalendarioRepository.existsByDataExcecao(dto.dataExcecao())) {
            throw new RegraNegocioException("Já existe uma exceção cadastrada para a data: " + dto.dataExcecao());
        }

        ExcecaoCalendario excecao = new ExcecaoCalendario();
        excecao.setDataExcecao(dto.dataExcecao());
        excecao.setTipo(dto.tipo());
        excecao.setDescricao(dto.descricao());

        excecao = excecaoCalendarioRepository.save(excecao);

        Map<String, Object> valorNovo = new HashMap<>();
        valorNovo.put("dataExcecao", excecao.getDataExcecao().toString());
        valorNovo.put("tipo", excecao.getTipo().name());
        valorNovo.put("descricao", excecao.getDescricao());

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "excecao_calendario",
                excecao.getExcecaoId(),
                AcaoSistema.CRIAR,
                null,
                valorNovo,
                "Cadastro de exceção de calendário"
        );

        return ExcecaoCalendarioResponseDTO.fromEntity(excecao);
    }

    @Transactional(readOnly = true)
    public List<ExcecaoCalendarioResponseDTO> listarTodas() {
        return excecaoCalendarioRepository.findAllByOrderByDataExcecaoAsc().stream()
                .map(ExcecaoCalendarioResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExcecaoCalendarioResponseDTO buscarPorId(Long id) {
        return ExcecaoCalendarioResponseDTO.fromEntity(buscarEntidadePorId(id));
    }

    public void deletar(Long id) {
        ExcecaoCalendario excecao = buscarEntidadePorId(id);

        Map<String, Object> valorAnterior = new HashMap<>();
        valorAnterior.put("dataExcecao", excecao.getDataExcecao().toString());
        valorAnterior.put("tipo", excecao.getTipo().name());
        valorAnterior.put("descricao", excecao.getDescricao());

        excecaoCalendarioRepository.delete(excecao);

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "excecao_calendario",
                id,
                AcaoSistema.EXCLUIR,
                valorAnterior,
                null,
                "Exclusão de exceção de calendário"
        );
    }

    private ExcecaoCalendario buscarEntidadePorId(Long id) {
        return excecaoCalendarioRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Exceção de calendário não encontrada: id " + id));
    }
}
