package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.turma.TurmaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.mapper.TurmaMapper;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaSpecification;
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
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final TurmaMapper turmaMapper;
    private final AuditoriaService auditoriaService;

    public TurmaResponseDTO cadastrar(TurmaRequestDTO dto) {
        Turma turma = turmaMapper.paraNovaEntidade(dto);
        turma = turmaRepository.save(turma);

        auditoriaService.registrar(
                ModuloSistema.TURMAS,
                "turma",
                turma.getTurmaId(),
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "nomeTurma", turma.getNomeTurma(),
                        "turno", turma.getTurno().name(),
                        "ativo", turma.getAtivo().toString()
                ),
                "Cadastro de turma"
        );

        return turmaMapper.paraDTO(turma);
    }

    public TurmaResponseDTO buscarPorId(Long id) {
        return turmaMapper.paraDTO(buscarEntidadePorId(id));
    }

    public List<TurmaResponseDTO> listarTodas() {
        return turmaRepository.findAll().stream()
                .map(TurmaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public List<TurmaResponseDTO> listarComFiltros(String nome, Turno turno, Boolean ativo) {
        Specification<Turma> spec = TurmaSpecification.comFiltros(nome, turno, ativo);
        return turmaRepository.findAll(spec).stream()
                .map(TurmaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public TurmaResponseDTO atualizar(Long id, TurmaRequestDTO dto) {
        Turma turma = buscarEntidadePorId(id);

        Map<String, Object> valorAnterior = Map.of(
                "nomeTurma", turma.getNomeTurma(),
                "turno", turma.getTurno().name(),
                "capacidade", turma.getCapacidade().toString()
        );

        turmaMapper.atualizarEntidade(turma, dto);
        turma = turmaRepository.save(turma);

        auditoriaService.registrar(
                ModuloSistema.TURMAS,
                "turma",
                turma.getTurmaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of(
                        "nomeTurma", turma.getNomeTurma(),
                        "turno", turma.getTurno().name(),
                        "capacidade", turma.getCapacidade().toString()
                ),
                "Atualizacao de dados da turma"
        );

        return turmaMapper.paraDTO(turma);
    }

    public TurmaResponseDTO alterarStatus(Long id, TurmaStatusRequestDTO dto) {
        Turma turma = buscarEntidadePorId(id);

        boolean statusAnterior = turma.getAtivo();
        turma.setAtivo(dto.ativo());
        turma = turmaRepository.save(turma);

        auditoriaService.registrar(
                ModuloSistema.TURMAS,
                "turma",
                turma.getTurmaId(),
                AcaoSistema.EDITAR,
                Map.of("ativo", String.valueOf(statusAnterior)),
                Map.of("ativo", String.valueOf(turma.getAtivo())),
                dto.ativo() ? "Reativacao de turma" : "Inativacao de turma"
        );

        return turmaMapper.paraDTO(turma);
    }

    Turma buscarEntidadePorId(Long id) {
        return turmaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma nao encontrada: id " + id));
    }
}