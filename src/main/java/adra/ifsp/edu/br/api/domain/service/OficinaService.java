package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResumoDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.mapper.OficinaMapper;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaResumoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaResumoRepository.TurmaDaOficina;
import adra.ifsp.edu.br.api.domain.repository.OficinaSpecification;
import adra.ifsp.edu.br.api.domain.repository.OficinaTurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OficinaService {

    private final OficinaRepository oficinaRepository;
    private final OficinaMapper oficinaMapper;
    private final AuditoriaService auditoriaService;
    private final HistoricoOficineiroService historicoOficineiroService;
    private final OficinaResumoRepository oficinaResumoRepository;
    private final OficinaTurmaRepository oficinaTurmaRepository;
    private final TurmaRepository turmaRepository;

    public List<OficinaResponseDTO> minhasOficinas(Long idOficineiro) {
        List<Oficina> oficinas = oficinaRepository.findByOficineiroResponsavelUsuarioId(idOficineiro);

        if(oficinas == null){
            return null;
        }

        return paraDTOs(oficinas);
    }

    public OficinaResponseDTO cadastrar(OficinaRequestDTO dto) {
        Oficina oficina = oficinaMapper.paraNovaEntidade(dto);
        oficina = oficinaRepository.save(oficina);
        historicoOficineiroService.registrar(oficina);
        atualizarGrupos(oficina, dto.turmaIds());

        auditoriaService.registrar(
                ModuloSistema.OFICINAS,
                "oficina",
                oficina.getOficinaId(),
                AcaoSistema.CRIAR,
                null,
                Map.of(
                        "nomeOficina", oficina.getNomeOficina(),
                        "oficineiroResponsavelId", oficina.getOficineiroResponsavel() != null ? oficina.getOficineiroResponsavel().getUsuarioId().toString() : null,
                        "ativo", oficina.getAtivo().toString()),
                "Cadastro de oficina");

        return paraDTO(oficina);
    }

    public OficinaResponseDTO buscarPorId(Long id) {
        return paraDTO(buscarEntidadePorId(id));
    }

    public List<OficinaResponseDTO> listarComFiltros(String nome, Boolean ativo) {
        Specification<Oficina> spec = OficinaSpecification.comFiltros(nome, ativo);
        return paraDTOs(oficinaRepository.findAll(spec));
    }

    /**
     * Os grupos da lista passam a ser atendidos pela oficina; os que saíram da
     * lista têm o vínculo inativado. Lista nula não mexe nos grupos.
     */
    private void atualizarGrupos(Oficina oficina, List<Long> turmaIds) {
        if (turmaIds == null) {
            return;
        }

        List<OficinaTurma> vinculos = oficinaTurmaRepository.findByOficina_OficinaId(oficina.getOficinaId());
        vinculos.forEach(vinculo -> vinculo.setAtivo(turmaIds.contains(vinculo.getTurma().getTurmaId())));

        Set<Long> jaVinculados = vinculos.stream()
                .map(vinculo -> vinculo.getTurma().getTurmaId())
                .collect(Collectors.toSet());
        List<Long> novos = turmaIds.stream().filter(id -> !jaVinculados.contains(id)).distinct().toList();

        List<Turma> gruposNovos = turmaRepository.findAllById(novos);
        if (gruposNovos.size() != novos.size()) {
            throw new EntidadeNaoEncontradaException("Um ou mais grupos informados não foram encontrados.");
        }
        gruposNovos.forEach(grupo -> oficinaTurmaRepository.save(new OficinaTurma(oficina, grupo)));
    }

    /** Grupos ativos de cada oficina, numa query só. */
    private List<OficinaResponseDTO> paraDTOs(List<Oficina> oficinas) {
        List<Long> oficinaIds = oficinas.stream().map(Oficina::getOficinaId).toList();
        Map<Long, List<Long>> gruposPorOficina = oficinaIds.isEmpty() ? Map.of()
                : oficinaTurmaRepository.findByOficina_OficinaIdInAndAtivoTrue(oficinaIds).stream()
                        .collect(Collectors.groupingBy(
                                vinculo -> vinculo.getOficina().getOficinaId(),
                                Collectors.mapping(vinculo -> vinculo.getTurma().getTurmaId(), Collectors.toList())));

        return oficinas.stream()
                .map(oficina -> oficinaMapper.paraDTO(oficina, gruposPorOficina.getOrDefault(oficina.getOficinaId(), List.of())))
                .collect(Collectors.toList());
    }

    private OficinaResponseDTO paraDTO(Oficina oficina) {
        return paraDTOs(List.of(oficina)).get(0);
    }

    /**
     * Resumo dos grupos por oficina para os cards da listagem: dias e
     * vagas. Uma consulta para todas as oficinas, agrupada aqui.
     */
    @Transactional(readOnly = true)
    public List<OficinaResumoDTO> resumo() {
        return oficinaResumoRepository.turmasAtivas(LocalDate.now()).stream()
                .collect(Collectors.groupingBy(TurmaDaOficina::oficinaId))
                .entrySet().stream()
                .map(e -> new OficinaResumoDTO(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().mapToLong(TurmaDaOficina::alunos).sum(),
                        e.getValue().stream().mapToInt(t -> t.capacidade() == null ? 0 : t.capacidade()).sum(),
                        horarios(e.getValue())))
                .toList();
    }

    /** Grupos com os mesmos dias viram uma linha só; a mais comum primeiro. */
    private static List<OficinaResumoDTO.Horario> horarios(List<TurmaDaOficina> turmas) {
        Map<List<DayOfWeek>, Long> contagem = turmas.stream()
                .map(t -> dias(t.diasIso()))
                .filter(dias -> !dias.isEmpty())
                .collect(Collectors.groupingBy(dias -> dias, LinkedHashMap::new, Collectors.counting()));
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<List<DayOfWeek>, Long>comparingByValue().reversed())
                .map(e -> new OficinaResumoDTO.Horario(e.getKey(), e.getValue().intValue()))
                .toList();
    }

    /** "1,3,5" (ISO: 1 = segunda) → [MONDAY, WEDNESDAY, FRIDAY]. */
    private static List<DayOfWeek> dias(String diasIso) {
        if (diasIso == null || diasIso.isBlank()) return List.of();
        return Arrays.stream(diasIso.split(",")).map(d -> DayOfWeek.of(Integer.parseInt(d))).sorted().toList();
    }

    /**
     * Suporte ao alerta NAO BLOQUEANTE de duplicidade por nome (CA de FE):
     * apenas informa se ja existe, o front decide como exibir o aviso e
     * o cadastro nao e' impedido no backend.
     */
    public boolean existeComMesmoNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return false;
        }
        return oficinaRepository.existsByNomeOficinaIgnoreCase(nome.trim());
    }

    Oficina buscarEntidadePorId(Long id) {
        return oficinaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Oficina nao encontrada: id " + id));
    }

    public OficinaResponseDTO editar(Long id, OficinaRequestDTO dto) {

        Oficina oficina = buscarEntidadePorId(id);

        // Guardar valores anteriores para auditoria.
        // Usa HashMap (não Map.of) porque oficineiroResponsavelId pode ser
        // null em oficinas antigas (cadastradas antes desse campo virar
        // obrigatório no front) - Map.of lança NullPointerException em
        // qualquer valor null, mesmo que a chave exista.
        Map<String, Object> valorAnterior = new HashMap<>();
        valorAnterior.put("nomeOficina", oficina.getNomeOficina());
        valorAnterior.put("local", oficina.getLocal());
        valorAnterior.put(
                "oficineiroResponsavelId",
                oficina.getOficineiroResponsavel() != null ? oficina.getOficineiroResponsavel().getUsuarioId() : null);

        // Atualizar usando o mapper
        oficinaMapper.atualizarEntidade(oficina, dto);

        oficina = oficinaRepository.save(oficina);
        historicoOficineiroService.registrar(oficina);
        atualizarGrupos(oficina, dto.turmaIds());

        Map<String, Object> valorNovo = new HashMap<>();
        valorNovo.put("nomeOficina", oficina.getNomeOficina());
        valorNovo.put("local", oficina.getLocal());
        valorNovo.put(
                "oficineiroResponsavelId",
                oficina.getOficineiroResponsavel() != null ? oficina.getOficineiroResponsavel().getUsuarioId() : null);

        // Auditar
        auditoriaService.registrar(
                ModuloSistema.OFICINAS,
                "oficina",
                oficina.getOficinaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                valorNovo,
                "Edição de oficina");

        return paraDTO(oficina);
    }

    public OficinaResponseDTO inativar(Long id) {
        Oficina oficina = buscarEntidadePorId(id);

        if (!oficina.getAtivo()) {
            throw new IllegalArgumentException("Oficina já está inativada.");
        }

        oficina.setAtivo(false);
        oficina = oficinaRepository.save(oficina);

        auditoriaService.registrar(
                ModuloSistema.OFICINAS,
                "oficina",
                oficina.getOficinaId(),
                AcaoSistema.EDITAR,
                Map.of("ativo", true),
                Map.of("ativo", false),
                "Oficina inativada");

        return paraDTO(oficina);
    }

    public OficinaResponseDTO reativar(Long id) {

        Oficina oficina = buscarEntidadePorId(id);

        if (oficina.getAtivo()) {
            throw new IllegalArgumentException("Oficina já está ativa.");
        }

        oficina.setAtivo(true);

        oficina = oficinaRepository.save(oficina);

        auditoriaService.registrar(
                ModuloSistema.OFICINAS,
                "oficina",
                oficina.getOficinaId(),
                AcaoSistema.EDITAR,
                Map.of("ativo", false),
                Map.of("ativo", true),
                "Oficina reativada");

        return paraDTO(oficina);
    }

}
