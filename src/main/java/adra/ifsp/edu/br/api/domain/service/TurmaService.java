package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.CorrecaoDataEntradaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaComAlunosResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.VincularAlunosTurmaDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.VincularOficinasTurmaDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.mapper.AssistidoMapper;
import adra.ifsp.edu.br.api.domain.mapper.TurmaMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaTurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaSpecification;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final TurmaMapper turmaMapper;
    private final AuditoriaService auditoriaService;
    private final AssistidoRepository assistidoRepository;
    private final AssistidoMapper assistidoMapper;
    private final TurmaAlunosRepository turmaAlunosRepository;
    private final VinculoTurmaService vinculoTurmaService;
    private final OficinaTurmaRepository oficinaTurmaRepository;
    private final OficinaRepository oficinaRepository;

    /**
     * Grupos atendidos pelas oficinas do oficineiro, já com o nome da oficina
     * e a quantidade de alunos ativos — aba "Minhas turmas" do oficineiro.
     */
    public List<TurmaComAlunosResponseDTO> minhasTurmas(Long idOficineiro) {
        return oficinaTurmaRepository.findByOficina_OficineiroResponsavel_UsuarioIdAndAtivoTrue(idOficineiro).stream()
                .map(vinculo -> TurmaComAlunosResponseDTO.fromVinculo(
                        vinculo,
                        assistidoRepository.countByTurma_TurmaIdAndStatus(vinculo.getTurma().getTurmaId(), StatusGeral.ATIVO)
                ))
                .collect(Collectors.toList());
    }

    public List<TurmaResponseDTO> minhasTurmasPorOficina(Long idOficina) {
        List<Turma> grupos = oficinaTurmaRepository.findByOficina_OficinaIdAndAtivoTrue(idOficina).stream()
                .map(OficinaTurma::getTurma)
                .toList();
        return paraDTOs(grupos);
    }

    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public TurmaResponseDTO cadastrar(TurmaRequestDTO dto) {
        Turma turma = turmaMapper.paraNovaEntidade(dto);
        turma = turmaRepository.save(turma);
        atualizarOficinas(turma, dto.oficinaIds());

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

        return paraDTO(turma);
    }

    @Cacheable(CacheConfig.ESTRUTURA)
    public TurmaResponseDTO buscarPorId(Long id) {
        return paraDTO(buscarEntidadePorId(id));
    }

    public List<TurmaResponseDTO> listarTodas() {
        return paraDTOs(turmaRepository.findAll());
    }

    @Cacheable(CacheConfig.ESTRUTURA)
    public List<TurmaResponseDTO> listarComFiltros(String nome, Turno turno, Boolean ativo) {
        Specification<Turma> spec = TurmaSpecification.comFiltros(nome, turno, ativo);
        List<Turma> turmas = turmaRepository.findAll(spec);

        Map<Long, Integer> matriculadosPorTurma = contarAtivosPorTurma(turmas);
        Map<Long, List<Long>> oficinasPorTurma = oficinasPorTurma(turmas);

        return turmas.stream()
                .map(turma -> turmaMapper.paraDTO(
                        turma,
                        matriculadosPorTurma.getOrDefault(turma.getTurmaId(), 0),
                        oficinasPorTurma.getOrDefault(turma.getTurmaId(), List.of())))
                .collect(Collectors.toList());
    }

    /** Ações "Ver oficinas" do grupo: troca as oficinas que atendem o grupo. */
    public TurmaResponseDTO vincularOficinas(Long turmaId, VincularOficinasTurmaDTO dto) {
        Turma turma = buscarEntidadePorId(turmaId);
        atualizarOficinas(turma, dto.oficinaIds());
        return paraDTO(turma);
    }

    /**
     * As oficinas da lista passam a atender o grupo; as que saíram da lista têm
     * o vínculo inativado. Lista nula não mexe nas oficinas.
     */
    private void atualizarOficinas(Turma turma, List<Long> oficinaIds) {
        if (oficinaIds == null) {
            return;
        }

        List<OficinaTurma> vinculos = oficinaTurmaRepository.findByTurma_TurmaId(turma.getTurmaId());
        vinculos.forEach(vinculo -> vinculo.setAtivo(oficinaIds.contains(vinculo.getOficina().getOficinaId())));

        Set<Long> jaVinculadas = vinculos.stream()
                .map(vinculo -> vinculo.getOficina().getOficinaId())
                .collect(Collectors.toSet());
        List<Long> novas = oficinaIds.stream().filter(id -> !jaVinculadas.contains(id)).distinct().toList();

        List<Oficina> oficinasNovas = oficinaRepository.findAllById(novas);
        if (oficinasNovas.size() != novas.size()) {
            throw new EntidadeNaoEncontradaException("Uma ou mais oficinas informadas não foram encontradas.");
        }
        oficinasNovas.forEach(oficina -> oficinaTurmaRepository.save(new OficinaTurma(oficina, turma)));
    }

    /** Oficinas ativas de cada grupo, numa query só. */
    private Map<Long, List<Long>> oficinasPorTurma(List<Turma> turmas) {
        if (turmas.isEmpty()) {
            return Map.of();
        }

        List<Long> turmaIds = turmas.stream().map(Turma::getTurmaId).toList();

        return oficinaTurmaRepository.findByTurma_TurmaIdInAndAtivoTrue(turmaIds).stream()
                .collect(Collectors.groupingBy(
                        vinculo -> vinculo.getTurma().getTurmaId(),
                        Collectors.mapping(vinculo -> vinculo.getOficina().getOficinaId(), Collectors.toList())
                ));
    }

    private List<TurmaResponseDTO> paraDTOs(List<Turma> turmas) {
        Map<Long, List<Long>> oficinasPorTurma = oficinasPorTurma(turmas);
        return turmas.stream()
                .map(turma -> turmaMapper.paraDTO(turma, null, oficinasPorTurma.getOrDefault(turma.getTurmaId(), List.of())))
                .collect(Collectors.toList());
    }

    private TurmaResponseDTO paraDTO(Turma turma) {
        return paraDTOs(List.of(turma)).get(0);
    }

    /**
     * Contagem de assistidos ativos por turma, em uma única query agrupada
     * (AssistidoRepository#contarAtivosPorTurma) em vez de uma COUNT por
     * turma — a listagem geral de Turmas pode trazer dezenas de linhas e
     * cada uma precisa mostrar ocupação (matriculados/capacidade).
     */
    private Map<Long, Integer> contarAtivosPorTurma(List<Turma> turmas) {
        if (turmas.isEmpty()) {
            return Map.of();
        }

        List<Long> turmaIds = turmas.stream().map(Turma::getTurmaId).collect(Collectors.toList());

        return assistidoRepository.contarAtivosPorTurma(turmaIds, StatusGeral.ATIVO).stream()
                .collect(Collectors.toMap(
                        linha -> (Long) linha[0],
                        linha -> ((Long) linha[1]).intValue()
                ));
    }

    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public TurmaResponseDTO atualizar(Long id, TurmaRequestDTO dto) {
        Turma turma = buscarEntidadePorId(id);

        Map<String, Object> valorAnterior = Map.of(
                "nomeTurma", turma.getNomeTurma(),
                "turno", turma.getTurno().name(),
                "capacidade", turma.getCapacidade().toString()
        );

        turmaMapper.atualizarEntidade(turma, dto);
        turma = turmaRepository.save(turma);
        atualizarOficinas(turma, dto.oficinaIds());

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

        return paraDTO(turma);
    }

    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
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

        return paraDTO(turma);
    }

    /**
     * Botão "+" da tela de turmas: vincula um ou mais assistidos à turma.
     * Fecha o vínculo ativo anterior de cada um (em qualquer turma) e abre um
     * novo em turma_aluno — que é o que o CA-65 (chamada) usa pra montar o
     * roster de uma aula — e mantém Assistido.turma em sincronia (outras
     * telas/queries do sistema ainda leem esse ponteiro direto).
     */
    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public List<AssistidoResponseDTO> vincularAlunos(Long turmaId, VincularAlunosTurmaDTO dto) {
        Turma turma = buscarEntidadePorId(turmaId);

        if (!turma.getAtivo()) {
            throw new RegraNegocioException("Não é possível vincular atendidos a um grupo inativo.");
        }

        List<Assistido> assistidos = assistidoRepository.findAllById(dto.assistidoIds());
        if (assistidos.size() != dto.assistidoIds().size()) {
            throw new EntidadeNaoEncontradaException("Um ou mais atendidos informados não foram encontrados.");
        }

        for (Assistido assistido : assistidos) {
            vinculoTurmaService.vincular(assistido, turma);
            assistido.setTurma(turma);
        }
        assistidoRepository.saveAll(assistidos);

        auditoriaService.registrar(
                ModuloSistema.TURMAS,
                "turma_aluno",
                turmaId,
                AcaoSistema.CRIAR,
                null,
                Map.of("assistidosVinculados", String.valueOf(assistidos.size())),
                "Vínculo de assistidos à turma"
        );

        return assistidos.stream().map(assistidoMapper::paraDTO).collect(Collectors.toList());
    }

    /** Quem está vinculado ativamente à turma hoje (turma_aluno) — usado pra excluir do modal quem já está na turma. */
    public List<AssistidoResponseDTO> listarAlunosVinculados(Long turmaId) {
        Turma turma = buscarEntidadePorId(turmaId);

        return turmaAlunosRepository.findByTurmaAndStatusAndDataSaidaIsNull(turma, StatusGeral.ATIVO)
                .stream()
                .map(vinculo -> assistidoMapper.paraDTO(vinculo.getAssistido()))
                .collect(Collectors.toList());
    }

    /**
     * Pedido da ADRA (11/11): chamada de todo o mês. Corrige quando os atendidos entraram no grupo,
     * para a chamada dos dias anteriores ao vínculo mostrar todos. Devolve quantos mudaram.
     */
    @CacheEvict(cacheNames = {CacheConfig.ESTRUTURA, CacheConfig.PAINEL}, allEntries = true)
    public int corrigirDataEntrada(Long turmaId, CorrecaoDataEntradaRequestDTO dto) {
        Turma turma = buscarEntidadePorId(turmaId);
        int corrigidos = vinculoTurmaService.corrigirDataEntrada(turma, dto.dataEntrada(), dto.assistidoIds());
        auditoriaService.registrar(
                ModuloSistema.TURMAS,
                "turma_aluno",
                turmaId,
                AcaoSistema.EDITAR,
                null,
                Map.of("dataEntrada", dto.dataEntrada().toString(), "vinculosCorrigidos", String.valueOf(corrigidos)),
                "Correção da data de entrada no grupo"
        );
        return corrigidos;
    }

    Turma buscarEntidadePorId(Long id) {
        return turmaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Grupo nao encontrado: id " + id));
    }
}