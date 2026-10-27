package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.repository.AssistidoResponsavelRepository;
import adra.ifsp.edu.br.api.domain.model.AssistidoResponsavel;
import adra.ifsp.edu.br.api.domain.dto.presenca.AutorizadoRetiradaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.FrequenciaAulaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.AlunoGradeMensalDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.ColunaGradeDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.GradeMensalTurmaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaHistoricoDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.TipoColunaGrade;
import adra.ifsp.edu.br.api.domain.enums.TipoExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.mapper.PresencaMapper;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.ExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.ExcecaoCalendarioRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.EntidadeNaoEncontradaException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Modelo esparso: só existe linha em Presenca quando o assistido FALTOU
 * (FALTA ou FALTA_JUSTIFICADA). PRESENTE nunca vira linha — é o estado
 * implícito de quem está no roster da turma e não tem falta registrada
 * naquela aula. Por causa disso:
 *  - reenvio marcando PRESENTE quem antes tinha falta APAGA o registro,
 *    não faz update (CA-65.4 vale pra esse "voltar a atrás" também);
 *  - o GET por aula precisa do roster (TurmaAlunosRepository) pra saber
 *    quem existia pra poder inferir quem faltou "por omissão".
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PresencaService {

    private final AssistidoResponsavelRepository assistidoResponsavelRepository;
    private final AssistidoIndicadoresService indicadoresService;
    private final PresencaRepository presencaRepository;
    private final AulaRepository aulaRepository;
    private final AssistidoRepository assistidoRepository;
    private final TurmaRepository turmaRepository;
    private final TurmaAlunosRepository turmaAlunosRepository;
    private final ExcecaoCalendarioRepository excecaoCalendarioRepository;
    private final PresencaMapper presencaMapper;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    /**
     * Lançamento em lote — endpoint principal do CA-65 (POST /api/chamadas/aula/{id}).
     * PRESENTE não gera linha (e apaga uma linha de falta de reenvio anterior,
     * se existir); FALTA/FALTA_JUSTIFICADA fazem upsert por (aula, assistido).
     */
    public List<PresencaResponseDTO> registrarChamada(Long idAula, List<PresencaRequestDTO> presencasDto) {
        Aula aula = aulaRepository.findById(idAula)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + idAula));

        validarAulaRealizada(aula); // CA-65.3

        Map<Long, PresencaRequestDTO> dtoPorAssistidoId = presencasDto.stream()
                .collect(Collectors.toMap(
                        PresencaRequestDTO::assistidoId, dto -> dto, (a, b) -> b, LinkedHashMap::new));

        Map<Long, Assistido> assistidosPorId = assistidoRepository.findAllById(dtoPorAssistidoId.keySet()).stream()
                .collect(Collectors.toMap(Assistido::getAssistidoId, a -> a));

        Map<Long, Presenca> existentesPorAssistidoId = presencaRepository.findByAula(aula).stream()
                .collect(Collectors.toMap(p -> p.getAssistido().getAssistidoId(), p -> p));

        // CA-65.1 + CA-70.1/70.3: só dá pra lançar presença de quem estava
        // vinculado à turma na data da aula E não estava desligado antes/na
        // data dela (findElegiveisParaChamada já cruza os dois critérios).
        Set<Long> assistidosDaTurma = turmaAlunosRepository
                .findElegiveisParaChamada(aula.getTurma(), aula.getDataAula())
                .stream()
                .map(v -> v.getAssistido().getAssistidoId())
                .collect(Collectors.toSet());

        Usuario usuarioLogado = usuarioAutenticadoService.getUsuarioAtual();

        List<Presenca> paraSalvar = new ArrayList<>();
        List<Presenca> paraApagar = new ArrayList<>();

        for (Map.Entry<Long, PresencaRequestDTO> entrada : dtoPorAssistidoId.entrySet()) {
            Long assistidoId = entrada.getKey();
            PresencaRequestDTO dto = entrada.getValue();

            Assistido assistido = assistidosPorId.get(assistidoId);
            if (assistido == null) {
                throw new EntidadeNaoEncontradaException("Assistido não encontrado: id " + assistidoId);
            }

            if (!assistidosDaTurma.contains(assistidoId)) {
                throw new RegraNegocioException(
                        "Assistido id " + assistidoId + " não está vinculado à turma desta aula."
                );
            }

            Presenca existente = existentesPorAssistidoId.get(assistidoId);

            if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
                if (existente != null) {
                    paraApagar.add(existente); // reenvio "voltou" pra presente: some o registro de falta
                }
                continue; // presente nunca cria linha
            }

            Presenca presenca = existente != null ? existente : new Presenca();
            if (presenca.getPresencaId() == null) {
                presenca.setAula(aula);
                presenca.setAssistido(assistido);
                presenca.setCriadoPor(usuarioLogado);
            }
            presenca.setStatusPresenca(dto.statusPresenca());
            presenca.setAtualizadoPor(usuarioLogado);
            paraSalvar.add(presenca);
        }

        if (!paraApagar.isEmpty()) {
            presencaRepository.deleteAll(paraApagar); // cascade apaga falta_justificada junto
        }

        List<Presenca> salvas = presencaRepository.saveAll(paraSalvar);

        // satélite de falta justificada — precisa do presencaId já persistido (CA-65.2)
        salvas.forEach(presenca -> presencaMapper.sincronizarFaltaJustificada(
                presenca, dtoPorAssistidoId.get(presenca.getAssistido().getAssistidoId())
        ));

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "chamada",
                idAula,
                AcaoSistema.CRIAR,
                null,
                Map.of("quantidadeFaltas", String.valueOf(salvas.size())),
                "Registro de chamada em lote"
        );

        // resposta ecoa os 3 status pro cliente, mesmo os PRESENTE não tendo linha
        Map<Long, Presenca> salvasPorAssistidoId = salvas.stream()
                .collect(Collectors.toMap(p -> p.getAssistido().getAssistidoId(), p -> p));

        return dtoPorAssistidoId.keySet().stream()
                .map(assistidoId -> {
                    PresencaRequestDTO dto = dtoPorAssistidoId.get(assistidoId);
                    if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
                        return PresencaResponseDTO.presente(idAula, assistidosPorId.get(assistidoId));
                    }
                    return presencaMapper.paraDTO(salvasPorAssistidoId.get(assistidoId));
                })
                .toList();
    }

    /** Lançamento/edição de uma presença isolada — mesma regra esparsa do lote, num item só. */
    public PresencaResponseDTO registrarPresenca(PresencaRequestDTO dto) {
        Aula aula = aulaRepository.findById(dto.aulaId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + dto.aulaId()));
        Assistido assistido = assistidoRepository.findById(dto.assistidoId())
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + dto.assistidoId()));

        validarAulaRealizada(aula); // CA-65.3

        // CA-65.1 + CA-70.1/70.3
        boolean pertenceATurma = turmaAlunosRepository
                .findElegiveisParaChamada(aula.getTurma(), aula.getDataAula())
                .stream()
                .anyMatch(v -> v.getAssistido().getAssistidoId().equals(assistido.getAssistidoId()));
        if (!pertenceATurma) {
            throw new RegraNegocioException(
                    "Assistido não está vinculado à turma desta aula, ou já estava desligado na data."
            );
        }

        Optional<Presenca> existente = presencaRepository.findByAulaAndAssistido(aula, assistido);

        if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
            existente.ifPresent(presencaRepository::delete);
            auditoriaService.registrar(
                    ModuloSistema.CHAMADA, "presenca", null, AcaoSistema.EDITAR, null,
                    Map.of("aulaId", aula.getAulaId().toString(), "assistidoId", assistido.getAssistidoId().toString(),
                            "statusPresenca", "PRESENTE"),
                    "Registro de presença (sem linha — presente)"
            );
            return PresencaResponseDTO.presente(aula.getAulaId(), assistido);
        }

        Usuario usuarioLogado = usuarioAutenticadoService.getUsuarioAtual();

        Presenca presenca = existente.orElseGet(() -> {
            Presenca nova = new Presenca();
            nova.setAula(aula);
            nova.setAssistido(assistido);
            nova.setCriadoPor(usuarioLogado);
            return nova;
        });

        boolean eraNova = presenca.getPresencaId() == null;
        presenca.setStatusPresenca(dto.statusPresenca());
        presenca.setAtualizadoPor(usuarioLogado);
        presenca = presencaRepository.save(presenca);
        presencaMapper.sincronizarFaltaJustificada(presenca, dto); // CA-65.2

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                eraNova ? AcaoSistema.CRIAR : AcaoSistema.EDITAR,
                null,
                Map.of(
                        "aulaId", presenca.getAula().getAulaId().toString(),
                        "assistidoId", presenca.getAssistido().getAssistidoId().toString(),
                        "statusPresenca", presenca.getStatusPresenca().name()
                ),
                "Registro de presença"
        );

        return presencaMapper.paraDTO(presenca);
    }

    /**
     * GET /api/chamadas/aula/{id} — a chamada completa: roster da turma na data
     * da aula (TurmaAlunos) cruzado com as faltas registradas. Quem não tem
     * falta = esteve presente.
     */
    public List<PresencaResponseDTO> buscarPorAula(Long idAula) {
        Aula aula = aulaRepository.findById(idAula)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Aula não encontrada: id " + idAula));

        List<TurmaAlunos> vinculosAtivos = turmaAlunosRepository.findElegiveisParaChamada(
                aula.getTurma(), aula.getDataAula());

        List<Presenca> faltas = presencaRepository.findByAulaWithUsuarios(aula);

        List<PresencaResponseDTO> chamada = presencaMapper.montarChamadaCompleta(idAula, vinculosAtivos, faltas);

        List<Assistido> assistidosDoRoster = vinculosAtivos.stream()
                .map(TurmaAlunos::getAssistido)
                .toList();
        Map<Long, List<PresencaHistoricoDTO>> historicoPorAssistido =
                buscarHistoricoRecente(aula, assistidosDoRoster);

        Map<Long, IndicadorFrequencia> frequencias = indicadoresService.frequencias(assistidosDoRoster);
        int aulasPrevistas = (int) aulaRepository.countByTurmaAndStatusAulaNot(aula.getTurma(), StatusAula.CANCELADA);

        // Quem pode buscar cada aluno (1 query para o roster todo); principal primeiro.
        Map<Long, List<AutorizadoRetiradaDTO>> autorizadosPorAssistido = assistidosDoRoster.isEmpty()
                ? Map.of()
                : assistidoResponsavelRepository.findComResponsavelByAssistidoIds(
                                assistidosDoRoster.stream().map(Assistido::getAssistidoId).toList()).stream()
                        .filter(AssistidoResponsavel::isAutorizadoRetirada)
                        .sorted(Comparator.comparing(AssistidoResponsavel::isResponsavelPrincipal).reversed()
                                .thenComparing(v -> v.getResponsavel().getNomeCompleto()))
                        .collect(Collectors.groupingBy(
                                v -> v.getAssistido().getAssistidoId(),
                                Collectors.mapping(v -> new AutorizadoRetiradaDTO(
                                        v.getResponsavel().getNomeCompleto(),
                                        v.getParentesco(),
                                        v.getResponsavel().getTelefone()), Collectors.toList())));

        return chamada.stream()
                .map(dto -> {
                    IndicadorFrequencia freq = frequencias.getOrDefault(dto.assistidoId(), IndicadorFrequencia.SEM_AULAS);
                    return dto.comHistoricoRecente(historicoPorAssistido.getOrDefault(dto.assistidoId(), List.of()))
                            .comFrequencia(freq.percentual(), freq.totalAulas(), aulasPrevistas)
                            .comAutorizadosRetirada(autorizadosPorAssistido.getOrDefault(dto.assistidoId(), List.of()));
                })
                .toList();
    }

    /**
     * Pra cada assistido, monta o status (presente/falta/falta justificada)
     * das últimas aulas REALIZADA da turma antes da aula atual, mais recente
     * primeiro — alimenta a "faixa de dias" no roster da chamada. O alerta de
     * faltas seguidas é derivado disso no front (não duplicado aqui). Tudo em
     * 2 queries (aulas anteriores + faltas em lote), não 1 por aluno.
     */
    private Map<Long, List<PresencaHistoricoDTO>> buscarHistoricoRecente(Aula aulaAtual, List<Assistido> assistidos) {
        if (assistidos.isEmpty()) {
            return Map.of();
        }

        List<Aula> aulasAnteriores = aulaRepository
                .findTop10ByTurmaAndStatusAulaAndDataAulaLessThanOrderByDataAulaDesc(
                        aulaAtual.getTurma(), StatusAula.REALIZADA, aulaAtual.getDataAula());

        if (aulasAnteriores.isEmpty()) {
            return Map.of();
        }

        List<Presenca> faltas = presencaRepository.findByAulaInAndAssistidoIn(aulasAnteriores, assistidos);

        Map<Long, Map<Long, StatusPresenca>> statusPorAssistidoEAula = new HashMap<>();
        for (Presenca falta : faltas) {
            statusPorAssistidoEAula
                    .computeIfAbsent(falta.getAssistido().getAssistidoId(), k -> new HashMap<>())
                    .put(falta.getAula().getAulaId(), falta.getStatusPresenca());
        }

        Map<Long, List<PresencaHistoricoDTO>> resultado = new HashMap<>();
        for (Assistido assistido : assistidos) {
            Map<Long, StatusPresenca> statusDoAssistido =
                    statusPorAssistidoEAula.getOrDefault(assistido.getAssistidoId(), Map.of());

            List<PresencaHistoricoDTO> historico = new ArrayList<>();
            for (Aula aulaAnterior : aulasAnteriores) { // já vem ordenada da mais recente pra trás
                StatusPresenca status = statusDoAssistido.getOrDefault(aulaAnterior.getAulaId(), StatusPresenca.PRESENTE);
                historico.add(new PresencaHistoricoDTO(aulaAnterior.getDataAula(), status));
            }
            resultado.put(assistido.getAssistidoId(), historico);
        }
        return resultado;
    }

    /**
     * Histórico de um assistido — no modelo esparso isso só devolve as faltas
     * dele (não tem linha pra reconstituir "presente" sem varrer todas as aulas
     * de todas as turmas por onde ele passou). Se precisar de frequência
     * completa por assistido, avisa que a lógica é parecida com buscarPorAula,
     * só que iterando as aulas da(s) turma(s) do período em vez de uma só.
     */
    public List<PresencaResponseDTO> buscarPorAssistido(Long idAssistido) {
        Assistido assistido = assistidoRepository.findById(idAssistido)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + idAssistido));

        return presencaMapper.paraDTOList(presencaRepository.findByAssistidoWithUsuarios(assistido));
    }

    /**
     * Histórico de frequência completo de um assistido (aba "Chamadas" do
     * cadastro) — ao contrário de `buscarPorAssistido` (que só devolve as
     * faltas soltas), aqui reconstituímos a frequência de verdade: pra cada
     * vínculo de turma que o assistido já teve, cruzamos as aulas REALIZADA
     * do período do vínculo com as faltas dele (ausência de falta = presente,
     * mesmo modelo esparso de `buscarPorAula`). Mais recente primeiro.
     */
    public List<FrequenciaAulaDTO> buscarFrequenciaAssistido(Long idAssistido) {
        Assistido assistido = assistidoRepository.findById(idAssistido)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Assistido não encontrado: id " + idAssistido));

        List<TurmaAlunos> vinculos = turmaAlunosRepository.findByAssistidoOrderByDataEntradaDesc(assistido);
        if (vinculos.isEmpty()) {
            return List.of();
        }

        List<FrequenciaAulaDTO> resultado = new ArrayList<>();

        for (TurmaAlunos vinculo : vinculos) {
            LocalDate fim = vinculo.getDataSaida() != null ? vinculo.getDataSaida() : LocalDate.now();
            List<Aula> aulasDoVinculo = aulaRepository.findByTurmaAndStatusAulaAndDataAulaBetweenOrderByDataAulaAsc(
                    vinculo.getTurma(), StatusAula.REALIZADA, vinculo.getDataEntrada(), fim);
            if (aulasDoVinculo.isEmpty()) {
                continue;
            }

            List<Presenca> faltas = presencaRepository.findByAulaInAndAssistidoIn(aulasDoVinculo, List.of(assistido));
            Map<Long, StatusPresenca> statusPorAulaId = faltas.stream()
                    .collect(Collectors.toMap(p -> p.getAula().getAulaId(), Presenca::getStatusPresenca));

            for (Aula aula : aulasDoVinculo) {
                StatusPresenca status = statusPorAulaId.getOrDefault(aula.getAulaId(), StatusPresenca.PRESENTE);
                resultado.add(new FrequenciaAulaDTO(aula.getAulaId(), aula.getDataAula(), vinculo.getTurma().getNomeTurma(), status));
            }
        }

        resultado.sort(Comparator.comparing(FrequenciaAulaDTO::dataAula).reversed());
        return resultado;
    }

    /**
     * Grade mensal turma × dias (ver GradeMensalTurmaDTO), no formato da
     * planilha da instituição. Colunas = aulas do mês (qualquer status) +
     * feriados/recessos que caem num dia de aula da turma. Linhas = quem teve
     * vínculo com a turma em algum dia do mês, inclusive quem saiu ou foi
     * desligado no meio dele. 4 queries no total, não 1 por aluno/aula.
     */
    @Transactional(readOnly = true)
    public GradeMensalTurmaDTO buscarGradeMensalTurma(Long idTurma, YearMonth mes) {
        Turma turma = turmaRepository.findById(idTurma)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Turma não encontrada: id " + idTurma));

        LocalDate inicio = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();
        LocalDate hoje = LocalDate.now();

        List<Aula> aulas = aulaRepository.findByTurmaAndDataAulaBetweenOrderByDataAulaAsc(turma, inicio, fim);
        Set<LocalDate> datasComAula = aulas.stream().map(Aula::getDataAula).collect(Collectors.toSet());

        List<ColunaGradeDTO> colunas = new ArrayList<>();
        Map<LocalDate, Aula> aulaPorData = new HashMap<>();
        for (Aula aula : aulas) {
            aulaPorData.put(aula.getDataAula(), aula);
            colunas.add(new ColunaGradeDTO(aula.getDataAula(), aula.getAulaId(), tipoColuna(aula, hoje), rotuloColuna(aula)));
        }

        // Feriado/recesso não gera aula (AulaService pula a data), então só
        // aparece na grade se cair num dia da semana em que a turma teria aula.
        Set<DayOfWeek> diasDaTurma = turma.getDiasDaSemana() != null ? Set.copyOf(turma.getDiasDaSemana()) : Set.of();
        for (ExcecaoCalendario excecao : excecaoCalendarioRepository.findByDataExcecaoBetween(inicio, fim)) {
            LocalDate data = excecao.getDataExcecao();
            if (!datasComAula.contains(data) && diasDaTurma.contains(data.getDayOfWeek())) {
                String rotulo = excecao.getDescricao() != null && !excecao.getDescricao().isBlank()
                        ? excecao.getDescricao()
                        : rotuloTipoExcecao(excecao.getTipo());
                colunas.add(new ColunaGradeDTO(data, null, TipoColunaGrade.SEM_AULA, rotulo));
            }
        }
        colunas.sort(Comparator.comparing(ColunaGradeDTO::data));

        List<TurmaAlunos> vinculos = turmaAlunosRepository.findVinculosNoPeriodo(turma, inicio, fim);
        Map<Long, List<TurmaAlunos>> vinculosPorAssistido = vinculos.stream()
                .collect(Collectors.groupingBy(v -> v.getAssistido().getAssistidoId()));
        List<Assistido> assistidos = vinculos.stream()
                .map(TurmaAlunos::getAssistido)
                .collect(Collectors.toMap(Assistido::getAssistidoId, a -> a, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(Assistido::getNomeCompleto))
                .toList();

        List<Aula> aulasRealizadas = aulas.stream().filter(a -> a.getStatusAula() == StatusAula.REALIZADA).toList();
        Map<Long, Map<Long, StatusPresenca>> faltasPorAssistidoEAula = new HashMap<>();
        if (!aulasRealizadas.isEmpty() && !assistidos.isEmpty()) {
            for (Presenca falta : presencaRepository.findByAulaInAndAssistidoIn(aulasRealizadas, assistidos)) {
                faltasPorAssistidoEAula
                        .computeIfAbsent(falta.getAssistido().getAssistidoId(), k -> new HashMap<>())
                        .put(falta.getAula().getAulaId(), falta.getStatusPresenca());
            }
        }

        List<AlunoGradeMensalDTO> alunos = assistidos.stream()
                .map(assistido -> {
                    List<TurmaAlunos> vinculosDoAluno = vinculosPorAssistido.get(assistido.getAssistidoId());
                    Map<Long, StatusPresenca> faltas =
                            faltasPorAssistidoEAula.getOrDefault(assistido.getAssistidoId(), Map.of());

                    List<StatusPresenca> presencas = colunas.stream()
                            .map(coluna -> {
                                if (coluna.tipo() != TipoColunaGrade.AULA) return null;
                                LocalDate data = coluna.data();
                                boolean naTurma = vinculosDoAluno.stream().anyMatch(v ->
                                        !v.getDataEntrada().isAfter(data)
                                                && (v.getDataSaida() == null || !v.getDataSaida().isBefore(data)));
                                if (!naTurma || !AssistidoSpecification.elegivelParaChamada(assistido, data)) return null;
                                return faltas.getOrDefault(coluna.aulaId(), StatusPresenca.PRESENTE);
                            })
                            .collect(Collectors.toCollection(ArrayList::new));

                    boolean desligado = assistido.getStatus() != StatusGeral.ATIVO && assistido.getDataSaida() != null;
                    return new AlunoGradeMensalDTO(
                            assistido.getAssistidoId(),
                            assistido.getNomeCompleto(),
                            assistido.getDataNascimento(),
                            desligado ? assistido.getDataSaida() : null,
                            desligado ? assistido.getMotivoSaida() : null,
                            presencas);
                })
                .toList();

        return new GradeMensalTurmaDTO(turma.getTurmaId(), turma.getNomeTurma(),
                mes.getYear(), mes.getMonthValue(), colunas, alunos);
    }

    private static TipoColunaGrade tipoColuna(Aula aula, LocalDate hoje) {
        return switch (aula.getStatusAula()) {
            case REALIZADA -> TipoColunaGrade.AULA;
            case CANCELADA -> TipoColunaGrade.CANCELADA;
            case PLANEJADA, REMARCADA -> aula.getDataAula().isAfter(hoje) ? TipoColunaGrade.FUTURA : TipoColunaGrade.PENDENTE;
        };
    }

    /** Na planilha, a coluna de passeio/evento leva o nome dele — aqui vem das observações da aula cancelada. */
    private static String rotuloColuna(Aula aula) {
        if (aula.getStatusAula() != StatusAula.CANCELADA) return null;
        if (aula.getObservacoes() != null && !aula.getObservacoes().isBlank()) return aula.getObservacoes().trim();
        return "Sem aula";
    }

    private static String rotuloTipoExcecao(TipoExcecaoCalendario tipo) {
        return switch (tipo) {
            case FERIADO -> "Feriado";
            case RECESSO -> "Recesso";
            case FERIAS -> "Férias";
            case PONTO_FACULTATIVO -> "Ponto facultativo";
            case EVENTO_PASSEIO -> "Passeio";
            case OUTRO -> "Sem aula";
        };
    }

    /** No modelo esparso, "atualizar pra PRESENTE" apaga a linha em vez de guardar um status. */
    public PresencaResponseDTO atualizarPresenca(Long id, PresencaRequestDTO dto) {
        Presenca presenca = buscarEntidadePorId(id);
        Usuario usuarioLogado = usuarioAutenticadoService.getUsuarioAtual();

        validarPermissaoCorrecaoPorData(presenca.getAula(), usuarioLogado); // US-67
        validarAulaRealizada(presenca.getAula()); // CA-65.3 vale pra edição também

        // CA-70.3: "corrigir" chamada de quem já estava desligado na data da
        // aula também é rejeitado (não só o lançamento novo via POST). Aulas
        // anteriores à saída continuam elegíveis, então isso não trava
        // correção de histórico (CA-70.2).
        Long assistidoIdDaPresenca = presenca.getAssistido().getAssistidoId();
        boolean elegivel = turmaAlunosRepository
                .findElegiveisParaChamada(presenca.getAula().getTurma(), presenca.getAula().getDataAula())
                .stream()
                .anyMatch(v -> v.getAssistido().getAssistidoId().equals(assistidoIdDaPresenca));
        if (!elegivel) {
            throw new RegraNegocioException(
                    "Assistido não está vinculado à turma desta aula, ou já estava desligado na data."
            );
        }

        if (dto.statusPresenca() == StatusPresenca.PRESENTE) {
            Long aulaId = presenca.getAula().getAulaId();
            Long assistidoId = presenca.getAssistido().getAssistidoId();
            Assistido assistidoDaPresenca = presenca.getAssistido();

            presencaRepository.delete(presenca);

            auditoriaService.registrar(
                    ModuloSistema.CHAMADA, "presenca", id, AcaoSistema.EXCLUIR,
                    Map.of("statusPresencaAnterior", presenca.getStatusPresenca().name()),
                    Map.of("statusPresenca", "PRESENTE"),
                    "Atualização de presença (virou presente — linha removida)"
            );

            return PresencaResponseDTO.presente(aulaId, assistidoDaPresenca);
        }

        Map<String, Object> valorAnterior = Map.of("statusPresenca", presenca.getStatusPresenca().name());

        presencaMapper.atualizarEntidade(presenca, dto);
        presenca.setAtualizadoPor(usuarioLogado);
        presenca = presencaRepository.save(presenca);
        presencaMapper.sincronizarFaltaJustificada(presenca, dto);

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                AcaoSistema.EDITAR,
                valorAnterior,
                Map.of("statusPresenca", presenca.getStatusPresenca().name()),
                "Atualização de presença"
        );

        return presencaMapper.paraDTO(presenca);
    }

    /** Apagar uma falta é, por natureza, o mesmo que dizer que o assistido esteve presente. */
    public void deletarPresenca(Long id) {
        Presenca presenca = buscarEntidadePorId(id);
        presencaRepository.delete(presenca); // falta_justificada some junto (ON DELETE CASCADE na migration)

        auditoriaService.registrar(
                ModuloSistema.CHAMADA,
                "presenca",
                presenca.getPresencaId(),
                AcaoSistema.EXCLUIR,
                Map.of(
                        "aulaId", presenca.getAula().getAulaId().toString(),
                        "assistidoId", presenca.getAssistido().getAssistidoId().toString()
                ),
                null,
                "Exclusão de presença"
        );
    }

    /**
     * US-67: Coordenador corrige chamada de qualquer data; Sociopedagógico só
     * pode corrigir a chamada do dia atual — evita abrir brecha de alteração
     * indevida de histórico por quem lança a chamada no dia a dia.
     */
    private void validarPermissaoCorrecaoPorData(Aula aula, Usuario usuarioLogado) {
        NomeNivelPermissao perfil = usuarioLogado.getNivelPermissao().getNome();
        boolean ehSociopedagogicoForaDoDia =
                perfil == NomeNivelPermissao.SOCIOPEDAGOGICO && !aula.getDataAula().isEqual(LocalDate.now());

        if (ehSociopedagogicoForaDoDia) {
            throw new AcessoNegadoException(
                    "Sociopedagógico só pode corrigir a chamada do dia atual (aula de "
                            + aula.getDataAula() + ")."
            );
        }
    }

    private void validarAulaRealizada(Aula aula) {
        if (aula.getStatusAula() != StatusAula.REALIZADA) {
            throw new RegraNegocioException(
                    "Só é possível lançar chamada em aulas com status REALIZADA (status atual: "
                            + aula.getStatusAula() + ")."
            );
        }
    }

    private Presenca buscarEntidadePorId(Long id) {
        return presencaRepository.findById(id)
                .orElseThrow(() -> new EntidadeNaoEncontradaException("Presença não encontrada: id " + id));
    }
}
