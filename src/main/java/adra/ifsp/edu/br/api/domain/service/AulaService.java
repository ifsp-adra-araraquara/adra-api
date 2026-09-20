package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.aula.AulaComDetalhesResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaStatusPatchRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.GerarAulasResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.AulaMapper;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.CriacaoAulas;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaSpecification;
import adra.ifsp.edu.br.api.domain.repository.ExcecaoCalendarioRepository;
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
    private final ExcecaoCalendarioRepository excecaoCalendarioRepository;
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

    public GerarAulasResponseDTO gerarAulas(CriacaoAulas criacaoAulas) {
        Turma turma = turmaRepository.findById(criacaoAulas.getTurmaId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma não encontrada: id " + criacaoAulas.getTurmaId()));

        Set<LocalDate> datasExcecao = excecaoCalendarioRepository.findDatasExcecaoBetween(
                criacaoAulas.getDataInicio(),
                criacaoAulas.getDataFim()
        );

        LocalDate dataAtual = criacaoAulas.getDataInicio();
        List<Aula> novasAulas = new ArrayList<>();
        int aulasCriadas = 0;
        int aulasExcecaoPuladas = 0;
        int aulasDuplicadasPuladas = 0;

        while (!dataAtual.isAfter(criacaoAulas.getDataFim())) {
            if (criacaoAulas.getDiasDaSemana().contains(dataAtual.getDayOfWeek())) {
                if (datasExcecao != null && datasExcecao.contains(dataAtual)) {
                    aulasExcecaoPuladas++;
                } else if (!aulaRepository.existsByTurmaAndDataAula(turma, dataAtual)) {
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

                    novasAulas.add(aulaRepository.save(aula));
                    aulasCriadas++;
                } else {
                    aulasDuplicadasPuladas++;
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
                        "aulasExcecaoPuladas", String.valueOf(aulasExcecaoPuladas),
                        "aulasDuplicadasPuladas", String.valueOf(aulasDuplicadasPuladas),
                        "periodo", criacaoAulas.getDataInicio() + " a " + criacaoAulas.getDataFim()
                ),
                "Criação em lote de aulas para turma com suporte a exceções"
        );

        List<AulaResponseDTO> aulasDTO = novasAulas.stream()
                .map(aulaMapper::paraDTO)
                .collect(Collectors.toList());

        return new GerarAulasResponseDTO(aulasCriadas, aulasExcecaoPuladas, aulasDuplicadasPuladas, aulasDTO);
    }

    public Boolean cadastrarVariasAulas(CriacaoAulas criacaoAulas) {
        GerarAulasResponseDTO resultado = gerarAulas(criacaoAulas);
        return resultado.aulasCriadas() > 0;
    }

    public List<AulaResponseDTO> listarTodas() {
        return aulaRepository.findAll().stream()
                .map(aulaMapper::paraDTO)
                .collect(Collectors.toList());
    }

    public List<AulaResponseDTO> listarComFiltros(Long turmaId, String nomeTurma, String titulo) {
        return listarComFiltros(turmaId, null, null, null, nomeTurma, titulo);
    }

    public List<AulaResponseDTO> listarComFiltros(
            Long turmaId, Long oficinaId, LocalDate dataInicio, LocalDate dataFim, String nomeTurma, String titulo
    ) {
        Specification<Aula> spec = AulaSpecification.comFiltros(
                turmaId, oficinaId, dataInicio, dataFim, nomeTurma, titulo, null
        );
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

        Map<String, Object> valorAnterior = new HashMap<>();
        valorAnterior.put("dataAula", aula.getDataAula().toString());
        valorAnterior.put("titulo", aula.getTitulo());

        aulaMapper.atualizarEntidade(aula, dto);
        aula = aulaRepository.save(aula);

        Map<String, Object> valorNovo = new HashMap<>();
        valorNovo.put("dataAula", aula.getDataAula().toString());
        valorNovo.put("titulo", aula.getTitulo());

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "aula",
                aula.getAulaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                valorNovo,
                "Atualização de aula"
        );

        return aulaMapper.paraDTO(aula);
    }

    public AulaResponseDTO atualizarStatus(Long id, AulaStatusPatchRequestDTO dto) {
        Aula aula = buscarEntidadePorId(id);

        Map<String, Object> valorAnterior = new HashMap<>();
        valorAnterior.put("statusAula", aula.getStatusAula() != null ? aula.getStatusAula().name() : null);

        aula.setStatusAula(dto.statusAula());
        aula = aulaRepository.save(aula);

        Map<String, Object> valorNovo = new HashMap<>();
        valorNovo.put("statusAula", aula.getStatusAula().name());

        auditoriaService.registrar(
                ModuloSistema.AULAS,
                "aula",
                aula.getAulaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                valorNovo,
                "Atualização pontual de status de aula"
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
