package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.chamada.AtendidoChamadaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadaGrupoDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadasDoDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.GrupoDoDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.HistoricoChamadaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.PresencaChamadaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.AutorizadoRetiradaDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusChamada;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.mapper.PresencaMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.FaltaJustificada;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.FaltaJustificadaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FT-12: chamada por grupo (turma) e dia. Modelo esparso, igual ao da chamada por aula:
 * só falta vira linha em Presenca; quem está no grupo e não tem falta esteve presente.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChamadaService {

    private final ChamadaRepository chamadaRepository;
    private final PresencaRepository presencaRepository;
    private final FaltaJustificadaRepository faltaJustificadaRepository;
    private final TurmaRepository turmaRepository;
    private final TurmaAlunosRepository turmaAlunosRepository;
    private final DiasDeChamadaService diasDeChamadaService;
    private final AssistidoIndicadoresService indicadoresService;
    private final PresencaMapper presencaMapper;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final FaltaPorSuspensaoService faltaPorSuspensaoService;

    @Transactional(readOnly = true)
    public ChamadasDoDiaDTO chamadasDoDia(LocalDate data) {
        String motivoSemChamada = diasDeChamadaService.motivoSemChamada(data);
        if (motivoSemChamada != null) {
            return new ChamadasDoDiaDTO(data, motivoSemChamada, List.of());
        }

        Map<Long, Chamada> lancadasPorTurmaId = chamadaRepository.findByDataChamada(data).stream()
                .collect(Collectors.toMap(chamada -> chamada.getTurma().getTurmaId(), Function.identity()));

        List<GrupoDoDiaDTO> grupos = turmaRepository.findAtivasComChamadaNoDia(data.getDayOfWeek()).stream()
                .map(turma -> grupoDoDia(turma, data, lancadasPorTurmaId.get(turma.getTurmaId())))
                .toList();

        return new ChamadasDoDiaDTO(data, null, grupos);
    }

    @Transactional(readOnly = true)
    public ChamadaGrupoDTO buscar(Long turmaId, LocalDate data) {
        Turma turma = buscarTurma(turmaId);
        Chamada chamada = chamadaRepository.findByTurmaAndDataChamada(turma, data).orElse(null);
        return montarChamada(turma, data, chamada);
    }

    @CacheEvict(cacheNames = CacheConfig.PAINEL, allEntries = true)
    public ChamadaGrupoDTO gravar(Long turmaId, LocalDate data, List<PresencaChamadaRequestDTO> presencas) {
        Turma turma = buscarTurma(turmaId);
        Usuario usuario = usuarioAutenticadoService.getUsuarioAtual();
        validarGravacao(turma, data, usuario);

        Map<Long, Assistido> atendidosPorId = atendidosNoDia(turma, data).stream()
                .collect(Collectors.toMap(Assistido::getAssistidoId, Function.identity()));
        Map<Long, LocalDate> suspensos = faltaPorSuspensaoService.suspensosEm(atendidosPorId.keySet(), data);
        validarPresencas(presencas, atendidosPorId, suspensos);

        Chamada existente = chamadaRepository.findByTurmaAndDataChamada(turma, data).orElse(null);
        Chamada chamada = existente != null ? existente : novaChamada(turma, data, usuario);
        chamada.setAtualizadoPor(usuario);
        chamada = chamadaRepository.save(chamada);

        int faltas = gravarPresencas(chamada, comSuspensos(presencas, suspensos), atendidosPorId, usuario);
        registrarAuditoria(chamada, existente == null, faltas);

        return montarChamada(turma, data, chamada);
    }

    private GrupoDoDiaDTO grupoDoDia(Turma turma, LocalDate data, Chamada chamada) {
        return new GrupoDoDiaDTO(
                turma.getTurmaId(),
                turma.getNomeTurma(),
                turma.getTurno(),
                atendidosNoDia(turma, data).size(),
                chamada != null ? StatusChamada.LANCADA : StatusChamada.PENDENTE,
                chamada != null ? chamada.getCriadoPor().getNomeCompleto() : null,
                chamada != null ? chamada.getCriadoEm() : null);
    }

    private ChamadaGrupoDTO montarChamada(Turma turma, LocalDate data, Chamada chamada) {
        List<Assistido> atendidos = atendidosNoDia(turma, data);
        Map<Long, Presenca> faltasPorAssistidoId = chamada == null ? Map.of() : presencaRepository.findByChamada(chamada).stream()
                .collect(Collectors.toMap(presenca -> presenca.getAssistido().getAssistidoId(), Function.identity()));
        Map<Long, FaltaJustificada> justificativasPorPresencaId = justificativas(faltasPorAssistidoId.values());
        Map<Long, List<HistoricoChamadaDTO>> historicoPorAssistidoId = historicoRecente(turma, data, atendidos);
        Map<Long, IndicadorFrequencia> frequencias = indicadoresService.frequencias(atendidos);
        Map<Long, List<AutorizadoRetiradaDTO>> autorizados = indicadoresService.autorizadosRetirada(atendidos);
        Map<Long, LocalDate> suspensos = faltaPorSuspensaoService.suspensosEm(
                atendidos.stream().map(Assistido::getAssistidoId).toList(), data);

        List<AtendidoChamadaDTO> linhas = atendidos.stream()
                .map(assistido -> {
                    Presenca falta = faltasPorAssistidoId.get(assistido.getAssistidoId());
                    FaltaJustificada justificativa = falta != null ? justificativasPorPresencaId.get(falta.getPresencaId()) : null;
                    LocalDate suspensoAte = suspensos.get(assistido.getAssistidoId());
                    return new AtendidoChamadaDTO(
                            assistido.getAssistidoId(),
                            assistido.getNomeCompleto(),
                            assistido.getDataNascimento(),
                            assistido.isPodeIrEmboraSozinho(),
                            suspensoAte != null ? StatusPresenca.FALTA_JUSTIFICADA
                                    : falta != null ? falta.getStatusPresenca() : StatusPresenca.PRESENTE,
                            suspensoAte != null ? MotivoFalta.SUSPENSAO
                                    : justificativa != null ? justificativa.getMotivoFalta() : null,
                            justificativa != null ? justificativa.getObservacao() : null,
                            historicoPorAssistidoId.getOrDefault(assistido.getAssistidoId(), List.of()),
                            frequencias.getOrDefault(assistido.getAssistidoId(), IndicadorFrequencia.SEM_AULAS).percentual(),
                            autorizados.getOrDefault(assistido.getAssistidoId(), List.of()),
                            suspensoAte);
                })
                .toList();

        return new ChamadaGrupoDTO(
                turma.getTurmaId(),
                turma.getNomeTurma(),
                data,
                chamada != null,
                podeEditar(turma, data, usuarioAutenticadoService.getUsuarioAtual()),
                chamada != null ? chamada.getCriadoPor().getNomeCompleto() : null,
                chamada != null ? chamada.getCriadoEm() : null,
                chamada != null ? chamada.getAtualizadoPor().getNomeCompleto() : null,
                chamada != null ? chamada.getAtualizadoEm() : null,
                linhas);
    }

    private Map<Long, FaltaJustificada> justificativas(Collection<Presenca> faltas) {
        if (faltas.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = faltas.stream().map(Presenca::getPresencaId).toList();
        return faltaJustificadaRepository.findByPresenca_PresencaIdIn(ids).stream()
                .collect(Collectors.toMap(fj -> fj.getPresenca().getPresencaId(), Function.identity()));
    }

    /** Status do atendido nas últimas chamadas do grupo antes do dia, a mais recente primeiro. */
    private Map<Long, List<HistoricoChamadaDTO>> historicoRecente(Turma turma, LocalDate data, List<Assistido> atendidos) {
        List<Chamada> anteriores = chamadaRepository.findTop10ByTurmaAndDataChamadaLessThanOrderByDataChamadaDesc(turma, data);
        if (anteriores.isEmpty() || atendidos.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<Long, StatusPresenca>> faltasPorAssistidoEChamada = new HashMap<>();
        for (Presenca falta : presencaRepository.findByChamadaInAndAssistidoIn(anteriores, atendidos)) {
            faltasPorAssistidoEChamada
                    .computeIfAbsent(falta.getAssistido().getAssistidoId(), id -> new HashMap<>())
                    .put(falta.getChamada().getChamadaId(), falta.getStatusPresenca());
        }

        return atendidos.stream().collect(Collectors.toMap(
                Assistido::getAssistidoId,
                assistido -> anteriores.stream()
                        .map(anterior -> new HistoricoChamadaDTO(
                                anterior.getDataChamada(),
                                faltasPorAssistidoEChamada
                                        .getOrDefault(assistido.getAssistidoId(), Map.of())
                                        .getOrDefault(anterior.getChamadaId(), StatusPresenca.PRESENTE)))
                        .toList()));
    }

    /** FT-13: o suspenso fica sempre como falta justificada por Suspensão, venha o que vier (corrige-se a suspensão, não a chamada). */
    private List<PresencaChamadaRequestDTO> comSuspensos(List<PresencaChamadaRequestDTO> presencas,
                                                         Map<Long, LocalDate> suspensos) {
        Map<Long, PresencaChamadaRequestDTO> porAssistidoId = new LinkedHashMap<>();
        presencas.forEach(presenca -> porAssistidoId.put(presenca.assistidoId(), presenca));
        suspensos.keySet().forEach(assistidoId -> {
            PresencaChamadaRequestDTO enviada = porAssistidoId.get(assistidoId);
            porAssistidoId.put(assistidoId, new PresencaChamadaRequestDTO(assistidoId,
                    StatusPresenca.FALTA_JUSTIFICADA, MotivoFalta.SUSPENSAO, enviada != null ? enviada.observacao() : null));
        });
        return List.copyOf(porAssistidoId.values());
    }

    private int gravarPresencas(Chamada chamada, List<PresencaChamadaRequestDTO> presencas,
                                Map<Long, Assistido> atendidosPorId, Usuario usuario) {
        Map<Long, Presenca> existentesPorAssistidoId = presencaRepository.findByChamada(chamada).stream()
                .collect(Collectors.toMap(presenca -> presenca.getAssistido().getAssistidoId(), Function.identity()));

        int faltas = 0;
        for (PresencaChamadaRequestDTO dto : presencas) {
            Presenca existente = existentesPorAssistidoId.get(dto.assistidoId());
            if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
                if (existente != null) {
                    presencaRepository.delete(existente);
                }
                continue;
            }
            Presenca falta = existente != null ? existente : novaFalta(chamada, atendidosPorId.get(dto.assistidoId()), usuario);
            falta.setStatusPresenca(dto.statusPresenca());
            falta.setAtualizadoPor(usuario);
            falta = presencaRepository.save(falta);
            presencaMapper.sincronizarFaltaJustificada(falta, dto.motivoFalta(), dto.observacao());
            faltas++;
        }
        return faltas;
    }

    private Chamada novaChamada(Turma turma, LocalDate data, Usuario usuario) {
        Chamada chamada = new Chamada();
        chamada.setTurma(turma);
        chamada.setDataChamada(data);
        chamada.setCriadoPor(usuario);
        return chamada;
    }

    private Presenca novaFalta(Chamada chamada, Assistido assistido, Usuario usuario) {
        Presenca presenca = new Presenca();
        presenca.setChamada(chamada);
        presenca.setAssistido(assistido);
        presenca.setCriadoPor(usuario);
        return presenca;
    }

    private void registrarAuditoria(Chamada chamada, boolean nova, int faltas) {
        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "chamada",
                chamada.getChamadaId(),
                nova ? AcaoSistema.CRIAR : AcaoSistema.EDITAR,
                null,
                Map.of("turmaId", chamada.getTurma().getTurmaId().toString(),
                        "data", chamada.getDataChamada().toString(),
                        "quantidadeFaltas", String.valueOf(faltas)),
                "Chamada do grupo no dia");
    }

    /** Coordenador grava qualquer dia passado, até fora da grade; Educador Social, os dias do mês atual em que o grupo tem chamada. */
    private void validarGravacao(Turma turma, LocalDate data, Usuario usuario) {
        if (data.isAfter(LocalDate.now())) {
            throw new RegraNegocioException("Não é possível lançar a chamada de um dia futuro.");
        }
        String motivoSemChamada = diasDeChamadaService.motivoSemChamada(data);
        if (motivoSemChamada != null) {
            throw new RegraNegocioException("Não há chamada neste dia: " + motivoSemChamada + ".");
        }
        if (ehCoordenador(usuario)) {
            return;
        }
        if (!noMesAtual(data)) {
            throw new AcessoNegadoException("O Educador Social só lança e corrige a chamada do mês atual.");
        }
        if (!diasDeChamadaService.temChamadaNoDia(turma, data)) {
            throw new RegraNegocioException("O grupo " + turma.getNomeTurma() + " não tem chamada neste dia da semana.");
        }
    }

    /** Valida a lista inteira antes de gravar qualquer coisa: a chamada entra completa ou não entra (CA-FT12.5). */
    private void validarPresencas(List<PresencaChamadaRequestDTO> presencas, Map<Long, Assistido> atendidosPorId,
                                  Map<Long, LocalDate> suspensos) {
        for (PresencaChamadaRequestDTO dto : presencas) {
            if (!atendidosPorId.containsKey(dto.assistidoId())) {
                throw new RegraNegocioException(
                        "O atendido id " + dto.assistidoId() + " não estava no grupo nesta data.");
            }
            if (suspensos.containsKey(dto.assistidoId())) {
                continue; // o back grava como falta justificada por Suspensão (comSuspensos)
            }
            if (dto.statusPresenca() == StatusPresenca.FALTA_JUSTIFICADA) {
                PresencaMapper.validarJustificativa(dto.motivoFalta(), dto.observacao());
                if (dto.motivoFalta() == MotivoFalta.SUSPENSAO) {
                    throw new RegraNegocioException("A falta por Suspensão vem do Sistema Disciplinar. "
                            + "Para " + atendidosPorId.get(dto.assistidoId()).getNomeCompleto() + ", use Atestado.");
                }
            }
        }
    }

    private boolean podeEditar(Turma turma, LocalDate data, Usuario usuario) {
        if (data.isAfter(LocalDate.now()) || diasDeChamadaService.motivoSemChamada(data) != null) {
            return false;
        }
        NomeNivelPermissao perfil = usuario.getNivelPermissao().getNome();
        if (perfil == NomeNivelPermissao.COORDENADOR) {
            return true;
        }
        return perfil == NomeNivelPermissao.EDUCADOR_SOCIAL
                && noMesAtual(data)
                && diasDeChamadaService.temChamadaNoDia(turma, data);
    }

    /** Pedido da ADRA (11/11): o Educador Social lança e corrige qualquer dia do mês vigente, até hoje. */
    private static boolean noMesAtual(LocalDate data) {
        LocalDate hoje = LocalDate.now();
        return !data.isAfter(hoje) && YearMonth.from(data).equals(YearMonth.from(hoje));
    }

    private boolean ehCoordenador(Usuario usuario) {
        return usuario.getNivelPermissao().getNome() == NomeNivelPermissao.COORDENADOR;
    }

    /** Quem estava no grupo na data (vínculo e assistido elegíveis), em ordem de nome. */
    private List<Assistido> atendidosNoDia(Turma turma, LocalDate data) {
        return turmaAlunosRepository.findElegiveisParaChamada(turma, data).stream()
                .map(TurmaAlunos::getAssistido)
                .distinct()
                .sorted(Comparator.comparing(Assistido::getNomeCompleto, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Turma buscarTurma(Long turmaId) {
        return turmaRepository.findById(turmaId)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Grupo não encontrado: id " + turmaId));
    }
}
