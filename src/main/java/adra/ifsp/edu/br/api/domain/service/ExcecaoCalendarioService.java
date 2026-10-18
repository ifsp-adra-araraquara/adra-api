package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.excecao.ExcecaoCalendarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.excecao.ExcecaoCalendarioResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.ExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.ExcecaoCalendarioRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ExcecaoCalendarioService {

    private final ExcecaoCalendarioRepository excecaoCalendarioRepository;
    private final AuditoriaService auditoriaService;
    private final AulaRepository aulaRepository;

    /**
     * Cadastra um dia sem aula — ou um período (dataFim), um registro por dia.
     * Dias que já têm exceção são mantidos como estão. Aulas PLANEJADA nesses
     * dias passam para CANCELADA (feriado, férias, ponto facultativo ou
     * evento/passeio no lugar da aula).
     */
    public List<ExcecaoCalendarioResponseDTO> cadastrar(ExcecaoCalendarioRequestDTO dto) {
        LocalDate inicio = dto.dataExcecao();
        LocalDate fim = dto.dataFim() != null ? dto.dataFim() : inicio;

        Set<LocalDate> jaCadastradas = excecaoCalendarioRepository.findDatasExcecaoBetween(inicio, fim);
        if (inicio.equals(fim) && jaCadastradas.contains(inicio)) {
            throw new RegraNegocioException("Já existe uma exceção cadastrada para a data: " + inicio);
        }

        List<ExcecaoCalendario> criadas = new ArrayList<>();
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            if (jaCadastradas.contains(data)) {
                continue;
            }
            ExcecaoCalendario excecao = new ExcecaoCalendario();
            excecao.setDataExcecao(data);
            excecao.setTipo(dto.tipo());
            excecao.setDescricao(dto.descricao());
            criadas.add(excecaoCalendarioRepository.save(excecao));
        }

        List<Aula> aulasCanceladas = aulaRepository.findByDataAulaBetweenAndStatusAula(inicio, fim, StatusAula.PLANEJADA).stream()
                .filter(aula -> criadas.stream().anyMatch(e -> e.getDataExcecao().equals(aula.getDataAula())))
                .toList();
        aulasCanceladas.forEach(aula -> aula.setStatusAula(StatusAula.CANCELADA));

        for (ExcecaoCalendario excecao : criadas) {
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
        }
        for (Aula aula : aulasCanceladas) {
            auditoriaService.registrar(
                    ModuloSistema.AULAS,
                    "aula",
                    aula.getAulaId(),
                    AcaoSistema.EDITAR,
                    Map.of("statusAula", StatusAula.PLANEJADA.name()),
                    Map.of("statusAula", StatusAula.CANCELADA.name()),
                    "Aula cancelada: dia sem aula (" + dto.tipo().name() + ")"
            );
        }

        return criadas.stream().map(ExcecaoCalendarioResponseDTO::fromEntity).toList();
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
