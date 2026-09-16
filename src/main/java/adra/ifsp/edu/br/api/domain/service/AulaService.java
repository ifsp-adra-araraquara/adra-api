package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.aula.AulaComDetalhesResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.AulaMapper;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.CriacaoAulas;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaSpecification;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
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

    public Boolean cadastrarVariasAulas(CriacaoAulas criacaoAulas){
        Turma turma = turmaRepository.findById(criacaoAulas.getTurmaId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma não encontrada: id " + criacaoAulas.getTurmaId()));

        LocalDate dataAtual = criacaoAulas.getDataInicio();
        int aulasCriadas = 0;
        int aulasPuladas = 0;

        while (!dataAtual.isAfter(criacaoAulas.getDataFim())) {
            if (criacaoAulas.getDiasDaSemana().contains(dataAtual.getDayOfWeek())) {
                if (!aulaRepository.existsByTurmaAndDataAula(turma, dataAtual)) {
                    Aula aula = new Aula();
                    aula.setTurma(turma);
                    aula.setDataAula(dataAtual);
                    aula.setHorarioInicio(criacaoAulas.getHorarioInicio());
                    aula.setHorarioFim(criacaoAulas.getHorarioFim());
                    aula.setTitulo(criacaoAulas.getTitulo());
                    aula.setDescricao(criacaoAulas.getDescricao());
                    aula.setConteudoPrevisto(criacaoAulas.getConteudoPrevisto());
                    aula.setObjetivos(criacaoAulas.getObjetivos());
                    aula.setRecursosNecessarios(criacaoAulas.getRecursosNecessarios());
                    aula.setObservacoes(criacaoAulas.getObservacoes());

                    aulaRepository.save(aula);
                    aulasCriadas++;
                } else {
                    aulasPuladas++;
                }
            }
            dataAtual = dataAtual.plusDays(1);
        }

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "aula",
                turma.getTurmaId(),
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "turmaId", turma.getTurmaId().toString(),
                        "quantidadeAulas", String.valueOf(aulasCriadas),
                        "aulasPuladas", String.valueOf(aulasPuladas),
                        "periodo", criacaoAulas.getDataInicio() + " a " + criacaoAulas.getDataFim()
                ),
                "Criação em lote de aulas para turma"
        );

        return aulasCriadas > 0;
    }

    public List<AulaResponseDTO> listarTodas() {
        return aulaRepository.findAll().stream()
                .map(aulaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public List<AulaResponseDTO> listarComFiltros(Long turmaId, String nomeTurma, String titulo) {
        Specification<Aula> spec = AulaSpecification.comFiltros(turmaId, nomeTurma, titulo);
        return aulaRepository.findAll(spec).stream()
                .map(aulaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    /**
     * Igual a listarComFiltros, mas ja enriquecido com nome da turma,
     * oficineiro responsavel e quantidade de alunos ativos — usado na tela
     * "Aulas" do sociopedagogico/coordenador, que por padrao mostra as
     * aulas de hoje (dataAula = hoje) com um filtro de data em cima pra
     * trocar o dia, sem precisar saber turmaId de antemao.
     */
    public List<AulaComDetalhesResponseDTO> listarComDetalhesComFiltros(
            Long turmaId, String nomeTurma, String titulo, LocalDate dataAula
    ) {
        Specification<Aula> spec = AulaSpecification.comFiltros(turmaId, nomeTurma, titulo, dataAula);

        List<Aula> aulas = aulaRepository.findAll(spec);
        aulas.sort(
                Comparator.comparing(Aula::getDataAula)
                        .thenComparing(Aula::getHorarioInicio, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        Map<Long, Integer> quantidadeAlunosPorTurma = new HashMap<>();
        List<AulaComDetalhesResponseDTO> resultado = new ArrayList<>();

        for (Aula aula : aulas) {
            Turma turma = aula.getTurma();
            Long turmaIdAula = (turma != null) ? turma.getTurmaId() : null;

            Integer quantidadeAlunos = 0;
            if (turmaIdAula != null) {
                Integer emCache = quantidadeAlunosPorTurma.get(turmaIdAula);
                if (emCache == null) {
                    long total = aulaRepository.countAlunosAtivosPorTurma(turmaIdAula);
                    emCache = (int) total;
                    quantidadeAlunosPorTurma.put(turmaIdAula, emCache);
                }
                quantidadeAlunos = emCache;
            }

            resultado.add(aulaMapper.paraDTOComDetalhes(aula, quantidadeAlunos));
        }

        return resultado;
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

    public List<AulaComDetalhesResponseDTO> findByTurmaComDetalhes(Long idTurma) {
        Optional<Turma> turma = turmaRepository.findById(idTurma);

        if (turma.isEmpty()) {
            throw new EntidadeNaoEncontradaException("Turma não encontrada: id " + idTurma);
        }

        List<Aula> aulas = aulaRepository.findByTurma(turma.get());
        long quantidadeAlunos = aulaRepository.countAlunosAtivosPorTurma(idTurma);

        return aulas.stream()
                .map(aula -> aulaMapper.paraDTOComDetalhes(aula, (int) quantidadeAlunos))
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
