package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.aula.AulaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.AulaMapper;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AulaService {
    private final AulaRepository aulaRepository;
    private final TurmaRepository turmaRepository;
    private final AulaMapper aulaMapper;
    private final AuditoriaService auditoriaService;

    public AulaResponseDTO cadastrar(AulaRequestDTO dto) {
        Aula aula = aulaMapper.paraNovaEntidade(dto);
        aula = aulaRepository.save(aula);

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "aula",
                aula.getAulaId(),
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "dataAula", aula.getDataAula().toString(),
                        "turmaId", aula.getTurma().getTurmaId().toString()
                ),
                "Cadastro de aula"
        );

        return aulaMapper.paraDTO(aula);
    }

    public List<AulaResponseDTO> listarTodas() {
        return aulaRepository.findAll().stream()
                .map(aulaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public AulaResponseDTO buscarPorId(Long id) {
        return aulaMapper.paraDTO(buscarEntidadePorId(id));
    }

    public List<AulaResponseDTO> findByTurma(Long idTurma) {
        Optional<Turma> turma = turmaRepository.findById(idTurma);

        if (turma.isEmpty()) {
            throw new EntidadeNaoEncontradaException("Turma não encontrada: id " + idTurma);
        }

        List<Aula> aulas = aulaRepository.findByTurma(turma.get());

        return aulas.stream()
                .map(aulaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public AulaResponseDTO atualizar(Long id, AulaRequestDTO dto) {
        Aula aula = buscarEntidadePorId(id);

        Map<String, Object> valorAnterior = Map.of(
                "dataAula", aula.getDataAula().toString(),
                "titulo", aula.getTitulo()
        );

        aulaMapper.atualizarEntidade(aula, dto);
        aula = aulaRepository.save(aula);

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "aula",
                aula.getAulaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of(
                        "dataAula", aula.getDataAula().toString(),
                        "titulo", aula.getTitulo()
                ),
                "Atualização de aula"
        );

        return aulaMapper.paraDTO(aula);
    }

    public void deletar(Long id) {
        Aula aula = buscarEntidadePorId(id);
        aulaRepository.delete(aula);

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "aula",
                aula.getAulaId(),
                AcaoSistema.EXCLUIR,
                Map.of("dataAula", aula.getDataAula().toString()),
                null,
                "Exclusão de aula"
        );
    }

    private Aula buscarEntidadePorId(Long id) {
        return aulaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + id));
    }
}
