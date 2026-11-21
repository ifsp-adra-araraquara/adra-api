package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.config.CacheConfig;
import adra.ifsp.edu.br.api.domain.dto.robo.AtendidoRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.ContagemDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.DisciplinarResumoRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.DistribuicaoAtendidosDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FaltasSeguidasRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FrequenciaDiaSemanaDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FrequenciaRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.MovimentoAtendidosDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.RoboContextoDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.SocialResumoDTO;
import adra.ifsp.edu.br.api.domain.enums.DimensaoAtendidos;
import adra.ifsp.edu.br.api.domain.model.FaixaEtaria;
import adra.ifsp.edu.br.api.domain.repository.PainelRepository;
import adra.ifsp.edu.br.api.domain.repository.RoboConsultaRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Robozinho ADRA — monta as respostas das perguntas do dia a dia a partir das
 * agregações do {@link RoboConsultaRepository}. Só leitura, só números já
 * somados no banco; listas nominais vêm cortadas em {@link #LIMITE_LISTA}.
 *
 * <p>Sem transação de propósito: cada consulta pega uma conexão, roda (uma por
 * vez na API, via {@link RoboLimitador}) e devolve; o cache de 5 min com
 * {@code sync} faz pedidos iguais simultâneos virarem uma consulta só.
 *
 * <p>Os métodos recebem {@code oficineiroId} (null = sem restrição) para que o
 * escopo do Oficineiro faça parte da chave do cache.
 */
@Service
@RequiredArgsConstructor
public class RoboConsultaService {

    /** Maior intervalo de uma consulta (meses). */
    static final int MAXIMO_MESES = 12;
    /** Itens por lista nominal; o total vem ao lado. */
    static final int LIMITE_LISTA = 50;
    /** Janela de chamadas para "faltas seguidas" (a mesma do Painel). */
    static final int DIAS_FALTAS_SEGUIDAS = 45;
    static final int LIMITE_MOTIVOS = 10;
    static final int LIMITE_BUSCA = 8;

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final RoboConsultaRepository repository;
    private final PainelRepository painelRepository;
    private final RoboLimitador limitador;

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public RoboContextoDTO contexto(Long oficineiroId) {
        return new RoboContextoDTO(
                repository.gruposAtivos(oficineiroId).stream()
                        .map(o -> new RoboContextoDTO.Opcao(o.id(), o.nome(), o.detalhe())).toList(),
                repository.oficinasAtivas(oficineiroId).stream()
                        .map(o -> new RoboContextoDTO.Opcao(o.id(), o.nome(), o.detalhe())).toList());
    }

    /** Até 8 ativos cujo nome contém todas as palavras do termo (sem acento, sem diferenciar maiúsculas). */
    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public List<AtendidoRoboDTO> buscarAtendidos(String nome, Long oficineiroId) {
        String termo = semAcento(nome == null ? "" : nome).trim();
        if (termo.length() < 2) {
            throw new RegraNegocioException("Digite pelo menos 2 letras do nome.");
        }
        String padrao = "%" + String.join("%", termo.split("\\s+")) + "%";
        return repository.buscarAtivosPorNome(padrao, LIMITE_BUSCA, oficineiroId).stream()
                .map(a -> new AtendidoRoboDTO(a.assistidoId(), a.nome(), a.grupo(), null, null))
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public List<AtendidoRoboDTO> aniversariantes(int mes, Long oficineiroId) {
        if (mes < 1 || mes > 12) {
            throw new RegraNegocioException("Mês inválido: use de 1 a 12.");
        }
        int ano = LocalDate.now().getYear();
        return repository.aniversariantesDoMes(mes, oficineiroId).stream()
                .map(a -> {
                    LocalDate dia = PainelService.proximoAniversario(a.nascimento(), LocalDate.of(ano, mes, 1));
                    return new AtendidoRoboDTO(a.assistidoId(), a.nome(), a.grupo(), dia,
                            (dia.getYear() - a.nascimento().getYear()) + " anos");
                })
                .toList();
    }

    /** Quem está a um registro da próxima medida (a mesma lista do Painel). */
    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public List<AtendidoRoboDTO> umPassoDaProximaMedida() {
        return limitador.executar(painelRepository::umPassoDaProgressao).stream()
                .limit(LIMITE_LISTA)
                .map(u -> new AtendidoRoboDTO(u.assistidoId(), u.nome(), null, null,
                        u.registros() + " " + u.nivel()))
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public FrequenciaRoboDTO frequencia(YearMonth de, YearMonth ate, Long oficineiroId) {
        validarIntervalo(de, ate);
        List<FrequenciaRoboDTO.Linha> linhas = repository
                .frequenciaPorTurmaEMes(de.atDay(1), ate.atEndOfMonth(), oficineiroId).stream()
                .map(l -> new FrequenciaRoboDTO.Linha(
                        YearMonth.from(l.mes()).format(MES), l.turmaId(), l.nomeTurma(), l.oficinas(),
                        l.chamadas(), l.esperados(), l.faltas(), l.justificadas(),
                        percentual(l.esperados(), l.faltas() + l.justificadas())))
                .toList();
        return new FrequenciaRoboDTO(de.format(MES), ate.format(MES), LocalDateTime.now(), linhas);
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public FrequenciaDiaSemanaDTO frequenciaPorDiaDaSemana(YearMonth de, YearMonth ate, Long turmaId, Long oficineiroId) {
        validarIntervalo(de, ate);
        List<FrequenciaDiaSemanaDTO.Dia> dias = repository
                .frequenciaPorDiaDaSemana(de.atDay(1), ate.atEndOfMonth(), turmaId, oficineiroId).stream()
                .map(d -> new FrequenciaDiaSemanaDTO.Dia(d.diaSemana(), d.chamadas(), d.esperados(), d.ausencias(),
                        percentual(d.esperados(), d.ausencias())))
                .toList();
        return new FrequenciaDiaSemanaDTO(de.format(MES), ate.format(MES), turmaId, LocalDateTime.now(), dias);
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public FaltasSeguidasRoboDTO faltasSeguidas(Integer minimo, Long oficineiroId) {
        int limiar = minimo == null ? IndicadorFrequencia.LIMIAR_FALTAS_CONSECUTIVAS : minimo;
        if (limiar < 2 || limiar > 20) {
            throw new RegraNegocioException("O número de faltas seguidas deve ficar entre 2 e 20.");
        }
        LocalDate hoje = LocalDate.now();
        LocalDate desde = hoje.minusDays(DIAS_FALTAS_SEGUIDAS);
        List<RoboConsultaRepository.FaltasSeguidas> todas = repository.faltasSeguidas(desde, hoje, limiar, oficineiroId);
        return new FaltasSeguidasRoboDTO(limiar, desde, LocalDateTime.now(),
                todas.stream().limit(LIMITE_LISTA)
                        .map(f -> new FaltasSeguidasRoboDTO.Item(f.assistidoId(), f.nome(), f.turmaId(), f.turma(),
                                f.faltas(), f.ultimaChamada()))
                        .toList(),
                todas.size());
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public DistribuicaoAtendidosDTO distribuicao(DimensaoAtendidos por) {
        LocalDate hoje = LocalDate.now();
        List<ContagemDTO> itens = switch (por) {
            case FAIXA_ETARIA -> limitador.executar(() -> painelRepository.ativosPorFaixaEtaria(hoje, null)).stream()
                    .map(f -> new ContagemDTO(f.idadeMinima() == null
                            ? PainelService.SEM_FAIXA
                            : FaixaEtaria.rotulo(f.idadeMinima(), f.idadeMaxima()), f.total()))
                    .toList();
            case IDADE -> repository.ativosPor(por.expressao(), por.juncao(), hoje).stream()
                    .sorted(Comparator.comparingInt(c -> Integer.parseInt(c.rotulo())))
                    .map(c -> new ContagemDTO(c.rotulo() + " anos", c.total()))
                    .toList();
            default -> repository.ativosPor(por.expressao(), por.juncao(), hoje).stream()
                    .map(c -> new ContagemDTO(c.rotulo(), c.total()))
                    .toList();
        };
        return new DistribuicaoAtendidosDTO(por.name(), repository.ativosHoje(hoje), LocalDateTime.now(), itens);
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public MovimentoAtendidosDTO movimento(int ano) {
        LocalDate hoje = LocalDate.now();
        if (ano < 2000 || ano > hoje.getYear()) {
            throw new RegraNegocioException("Ano fora do período com dados: use de 2000 até " + hoje.getYear() + ".");
        }
        LocalDate inicio = Year.of(ano).atDay(1);
        LocalDate fim = Year.of(ano).atMonth(12).atEndOfMonth();
        LocalDate referenciaFim = fim.isAfter(hoje) ? hoje : fim;

        Map<Integer, RoboConsultaRepository.MovimentoMes> porMes = repository.movimentoPorMes(inicio, fim).stream()
                .collect(Collectors.toMap(RoboConsultaRepository.MovimentoMes::mes, Function.identity()));
        List<MovimentoAtendidosDTO.Mes> meses = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            RoboConsultaRepository.MovimentoMes linha = porMes.get(m);
            meses.add(new MovimentoAtendidosDTO.Mes(m, linha == null ? 0 : linha.entradas(), linha == null ? 0 : linha.saidas()));
        }
        return new MovimentoAtendidosDTO(
                ano,
                repository.ativosEm(inicio.minusDays(1)),
                repository.ativosEm(referenciaFim),
                meses.stream().mapToLong(MovimentoAtendidosDTO.Mes::entradas).sum(),
                meses.stream().mapToLong(MovimentoAtendidosDTO.Mes::saidas).sum(),
                LocalDateTime.now(),
                meses,
                repository.motivosDeSaida(inicio, fim, LIMITE_MOTIVOS).stream()
                        .map(c -> new ContagemDTO(c.rotulo(), c.total()))
                        .toList());
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public DisciplinarResumoRoboDTO disciplinar(YearMonth mes) {
        YearMonth anterior = mes.minusMonths(1);
        List<DisciplinarResumoRoboDTO.Grupo> grupos = repository
                .disciplinaPorGrupo(anterior.atDay(1), mes.atDay(1), mes.atEndOfMonth()).stream()
                .map(g -> new DisciplinarResumoRoboDTO.Grupo(g.turmaId(), g.nomeTurma(), g.ocorrencias(),
                        g.advertencias(), g.suspensoes(), g.totalMesAnterior()))
                .toList();
        return new DisciplinarResumoRoboDTO(
                mes.format(MES),
                grupos.stream().mapToLong(DisciplinarResumoRoboDTO.Grupo::ocorrencias).sum(),
                grupos.stream().mapToLong(DisciplinarResumoRoboDTO.Grupo::advertencias).sum(),
                grupos.stream().mapToLong(DisciplinarResumoRoboDTO.Grupo::suspensoes).sum(),
                grupos.stream().mapToLong(DisciplinarResumoRoboDTO.Grupo::totalMesAnterior).sum(),
                LocalDateTime.now(),
                grupos);
    }

    @Cacheable(cacheNames = CacheConfig.ROBO, sync = true)
    public SocialResumoDTO social() {
        LocalDate hoje = LocalDate.now();
        RoboConsultaRepository.SocialTotais t = repository.socialTotais(hoje);
        return new SocialResumoDTO(
                t.ativos(),
                t.recebemPrograma(),
                contagens(repository.programasTransferencia(hoje)),
                t.rendaInformada(),
                contagens(repository.faixasDeRenda(hoje)),
                t.pcd(),
                t.necessidadesEspecificas(),
                t.problemaSaude(),
                t.comNis(),
                t.estudaContraturno(),
                contagens(repository.ativosPor(DimensaoAtendidos.TURNO_ESCOLA.expressao(),
                        DimensaoAtendidos.TURNO_ESCOLA.juncao(), hoje)),
                LocalDateTime.now());
    }

    private static List<ContagemDTO> contagens(List<RoboConsultaRepository.Contagem> lista) {
        return lista.stream().map(c -> new ContagemDTO(c.rotulo(), c.total())).toList();
    }

    static String semAcento(String texto) {
        return java.text.Normalizer.normalize(texto.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    static void validarIntervalo(YearMonth de, YearMonth ate) {
        if (de.isAfter(ate)) {
            throw new RegraNegocioException("O mês inicial vem depois do final.");
        }
        if (ChronoUnit.MONTHS.between(de, ate) + 1 > MAXIMO_MESES) {
            throw new RegraNegocioException("Escolha um período de até " + MAXIMO_MESES + " meses.");
        }
    }

    /** % de presença: (esperados − ausências) ÷ esperados; null sem esperados. Igual ao Painel. */
    static Integer percentual(long esperados, long ausencias) {
        if (esperados == 0) return null;
        return (int) Math.round((esperados - ausencias) * 100.0 / esperados);
    }
}
