package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.AtendidoChamadaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.FrequenciaDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadaGrupoDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.PresencaChamadaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.CorrecaoDataEntradaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ArquivarRegistroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.FaltaJustificadaRepository;
import adra.ifsp.edu.br.api.domain.repository.MotivoDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * FT-12: chamada por grupo e dia, service a service contra Postgres real
 * (Testcontainers). Cobre os critérios CA-FT12.1, .3, .4, .5 e .6 e as regras
 * que vinham da chamada por aula: desligado some depois da saída (CA-70) e
 * correção por data conforme o perfil (US-67).
 */
@SpringBootTest
@Testcontainers
@Import(ContainerPostgresAdra.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
@Transactional
class ChamadaServiceIT {

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private ChamadaRepository chamadaRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private ChamadaService chamadaService;
    @Autowired private AssistidoIndicadoresService indicadoresService;
    @Autowired private FrequenciaService frequenciaService;
    @Autowired private AssistidoService assistidoService;
    @Autowired private VinculoTurmaService vinculoTurmaService;
    @Autowired private DataSource dataSource;
    @Autowired private TurmaService turmaService;
    @Autowired private DisciplinarService disciplinarService;
    @Autowired private MotivoDisciplinarRepository motivoDisciplinarRepository;
    @Autowired private FaltaJustificadaRepository faltaJustificadaRepository;

    private static final LocalDate HOJE = LocalDate.now();

    private Usuario coordenador;
    private Usuario educador;
    private Oficina oficina;
    private Turma turma;
    private Assistido ana;
    private Assistido bruno;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        }

        coordenador = criarUsuario(NomeNivelPermissao.COORDENADOR, "coord-ft12@teste.local");
        educador = criarUsuario(NomeNivelPermissao.EDUCADOR_SOCIAL, "educador-ft12@teste.local");
        autenticarComo(coordenador);

        oficina = new Oficina();
        oficina.setNomeOficina("Oficina FT-12");
        oficina.setOficineiroResponsavel(coordenador);
        oficina = oficinaRepository.save(oficina);

        turma = criarGrupo("SI Manhã A", DayOfWeek.values());
        LocalDate entrada = HOJE.minusMonths(3);
        ana = matricular("Ana Chamada", turma, entrada, null);
        bruno = matricular("Bruno Chamada", turma, entrada, null);
    }

    @Test
    @DisplayName("Frequência respeita o período no grupo, não conta a chamada duas vezes e a justificada interrompe a sequência")
    void frequenciaCalculadaNoBanco() {
        LocalDate maisAntigo = diaSemFeriado(12);
        LocalDate segundo = diaSemFeriado(9);
        LocalDate terceiro = diaSemFeriado(6);
        LocalDate maisRecente = diaSemFeriado(3);
        LocalDate entrada = maisAntigo.minusDays(1);

        Assistido saiuNoSegundoDia = matricular("Carla Saiu", turma, entrada, segundo);
        Assistido voltouNoTerceiroDia = matricular("Davi Voltou", turma, entrada, terceiro);
        Assistido semChamadas = matricular("Eva Nova", turma, HOJE, null);

        PresencaChamadaRequestDTO justificada =
                new PresencaChamadaRequestDTO(ana.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, MotivoFalta.ATESTADO, null);
        chamadaService.gravar(turma.getTurmaId(), maisAntigo, List.of(presente(ana)));
        chamadaService.gravar(turma.getTurmaId(), segundo, List.of(justificada));
        chamadaService.gravar(turma.getTurmaId(), terceiro, List.of(falta(ana)));
        ChamadaGrupoDTO ultima = chamadaService.gravar(turma.getTurmaId(), maisRecente, List.of(falta(ana)));

        assertThat(linha(ultima, ana).frequenciaPercentual()).isEqualTo(25);

        voltarAoGrupo(voltouNoTerceiroDia, terceiro);

        var frequencias = indicadoresService.frequencias(List.of(ana, saiuNoSegundoDia, voltouNoTerceiroDia, semChamadas));
        assertThat(frequencias.get(ana.getAssistidoId()).totalAulas()).isEqualTo(4);
        assertThat(frequencias.get(ana.getAssistidoId()).faltasConsecutivas()).isEqualTo(2);
        assertThat(frequencias.get(ana.getAssistidoId()).emAcompanhamento()).isTrue();
        assertThat(frequencias.get(saiuNoSegundoDia.getAssistidoId()).totalAulas()).isEqualTo(2);
        assertThat(frequencias.get(voltouNoTerceiroDia.getAssistidoId()).totalAulas()).isEqualTo(4);
        assertThat(frequencias.get(semChamadas.getAssistidoId())).isEqualTo(IndicadorFrequencia.SEM_AULAS);
    }

    @Test
    @DisplayName("Saída e retorno ao grupo no mesmo dia: chamada e ficha não repetem o atendido nem o dia")
    void saidaERetornoNoMesmoDia() {
        LocalDate anterior = diaSemFeriado(6);
        LocalDate dia = diaSemFeriado(3);
        Assistido davi = matricular("Davi Voltou", turma, anterior.minusDays(1), dia);
        voltarAoGrupo(davi, dia);
        chamadaService.gravar(turma.getTurmaId(), anterior, List.of(presente(davi)));

        ChamadaGrupoDTO gravada = chamadaService.gravar(turma.getTurmaId(), dia, List.of(falta(davi)));
        ChamadaGrupoDTO aberta = chamadaService.buscar(turma.getTurmaId(), dia);

        assertThat(gravada.atendidos()).filteredOn(a -> a.assistidoId().equals(davi.getAssistidoId())).hasSize(1);
        assertThat(aberta.atendidos()).filteredOn(a -> a.assistidoId().equals(davi.getAssistidoId())).hasSize(1);
        assertThat(status(aberta, davi)).isEqualTo(StatusPresenca.FALTA);
        assertThat(frequenciaService.historicoDoAtendido(davi.getAssistidoId()))
                .extracting(FrequenciaDiaDTO::data)
                .containsExactly(dia, anterior);
    }

    @Test
    @DisplayName("CA-FT12.1: gravar de novo o mesmo dia atualiza a chamada, sem duplicar")
    void regravarAtualizaSemDuplicar() {
        LocalDate dia = diaSemFeriado(1);

        chamadaService.gravar(turma.getTurmaId(), dia, List.of(falta(ana)));
        ChamadaGrupoDTO regravada = chamadaService.gravar(turma.getTurmaId(), dia, List.of(presente(ana), falta(bruno)));

        assertThat(chamadaRepository.findByTurmaAndDataChamada(turma, dia)).isPresent();
        assertThat(chamadaRepository.findByTurmaAndDataChamadaBetween(turma, dia, dia)).hasSize(1);
        assertThat(status(regravada, ana)).isEqualTo(StatusPresenca.PRESENTE);
        assertThat(status(regravada, bruno)).isEqualTo(StatusPresenca.FALTA);
    }

    @Test
    @DisplayName("CA-FT12.3: a lista do dia traz quem estava no grupo naquela data")
    void listaSegueOVinculoNaData() {
        LocalDate dia = diaSemFeriado(10);
        Assistido entrouDepois = matricular("Carla Entrou Depois", turma, dia.plusDays(2), null);
        Assistido saiuDepois = matricular("Davi Saiu Depois", turma, dia.minusMonths(1), dia.plusDays(2));

        List<Long> atendidos = chamadaService.buscar(turma.getTurmaId(), dia).atendidos().stream()
                .map(AtendidoChamadaDTO::assistidoId).toList();

        assertThat(atendidos).contains(saiuDepois.getAssistidoId()).doesNotContain(entrouDepois.getAssistidoId());
    }

    @Test
    @DisplayName("CA-70: desligado some das chamadas depois da saída e não recebe lançamento")
    void desligadoSomeDepoisDaSaida() {
        LocalDate antesDaSaida = diaSemFeriado(10);
        LocalDate depoisDaSaida = diaSemFeriado(1);
        assistidoService.alterarStatus(ana.getAssistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, depoisDaSaida.minusDays(1), "Mudou de cidade"));

        assertThat(idsNaChamada(depoisDaSaida)).doesNotContain(ana.getAssistidoId());
        assertThat(idsNaChamada(antesDaSaida)).contains(ana.getAssistidoId());
        assertThatThrownBy(() -> chamadaService.gravar(turma.getTurmaId(), depoisDaSaida, List.of(falta(ana))))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Educador Social lança e corrige qualquer dia do mês atual; mês anterior, só o Coordenador")
    void edicaoPorPerfilEData() {
        LocalDate mesPassado = diaSemFeriadoAntesDe(HOJE.withDayOfMonth(1));

        autenticarComo(educador);
        if (HOJE.getDayOfMonth() > 1) {
            LocalDate doMes = diaSemFeriadoAntesDe(HOJE);
            assumeFalse(doMes.getMonthValue() != HOJE.getMonthValue(), "sem dia útil anterior neste mês");
            assertThat(chamadaService.buscar(turma.getTurmaId(), doMes).podeEditar()).isTrue();
            assertThat(chamadaService.gravar(turma.getTurmaId(), doMes, List.of(falta(ana))).lancada()).isTrue();
        }
        assertThat(chamadaService.buscar(turma.getTurmaId(), mesPassado).podeEditar()).isFalse();
        assertThatThrownBy(() -> chamadaService.gravar(turma.getTurmaId(), mesPassado, List.of(falta(ana))))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("mês atual");

        autenticarComo(coordenador);
        assertThat(chamadaService.gravar(turma.getTurmaId(), mesPassado, List.of(falta(ana))).lancada()).isTrue();
    }

    @Test
    @DisplayName("Corrigir a entrada no grupo faz o atendido aparecer na chamada dos dias anteriores ao vínculo")
    void corrigirEntradaNoGrupo() {
        Assistido ligadoHoje = matricular("Lia Ligada Hoje", turma, HOJE, null);
        LocalDate antes = diaSemFeriado(5);
        assertThat(idsNaChamada(antes)).doesNotContain(ligadoHoje.getAssistidoId());

        var resultado = turmaService.corrigirDataEntrada(turma.getTurmaId(),
                new CorrecaoDataEntradaRequestDTO(antes.minusDays(1), List.of(ligadoHoje.getAssistidoId())));

        assertThat(resultado.corrigidos()).isEqualTo(1);
        assertThat(resultado.chamadasLancadas()).isZero();
        assertThat(idsNaChamada(antes)).contains(ligadoHoje.getAssistidoId());
    }

    @Test
    @DisplayName("A correção não passa da saída do grupo anterior nem do início do ano")
    void correcaoRespeitaGrupoAnteriorEAno() {
        Turma anterior = criarGrupo("SI Tarde", DayOfWeek.values());
        LocalDate saidaAnterior = HOJE.minusDays(3);
        Assistido trocou = matricular("Téo Trocou", anterior, HOJE.minusMonths(1), saidaAnterior);
        TurmaAlunos atual = new TurmaAlunos();
        atual.setTurma(turma);
        atual.setAssistido(trocou);
        atual.setDataEntrada(HOJE);
        atual.setStatus(StatusGeral.ATIVO);
        turmaAlunosRepository.save(atual);

        turmaService.corrigirDataEntrada(turma.getTurmaId(),
                new CorrecaoDataEntradaRequestDTO(LocalDate.of(HOJE.getYear() - 1, 6, 1), List.of(trocou.getAssistidoId())));

        assertThat(turmaAlunosRepository.findByAssistidoAndStatusAndDataSaidaIsNull(trocou, StatusGeral.ATIVO))
                .singleElement().extracting(TurmaAlunos::getDataEntrada).isEqualTo(saidaAnterior);
    }

    @Test
    @DisplayName("Educador Social não grava dia fora da grade do grupo; Coordenador grava")
    void foraDaGradeSoCoordenador() {
        assumeFalse(FeriadosNacionais.ehFeriado(HOJE), "hoje é feriado: não há chamada");
        Turma soAmanha = criarGrupo("CJ A", HOJE.plusDays(1).getDayOfWeek());
        matricular("Eva Fora da Grade", soAmanha, HOJE.minusMonths(1), null);

        autenticarComo(educador);
        assertThatThrownBy(() -> chamadaService.gravar(soAmanha.getTurmaId(), HOJE, List.of()))
                .isInstanceOf(RegraNegocioException.class);

        autenticarComo(coordenador);
        assertThat(chamadaService.gravar(soAmanha.getTurmaId(), HOJE, List.of()).lancada()).isTrue();
    }

    @Test
    @DisplayName("CA-FT12.5: se um item é inválido, nada da chamada é gravado")
    void falhaNaoGravaNada() {
        LocalDate dia = diaSemFeriado(1);
        PresencaChamadaRequestDTO justificadaSemMotivo =
                new PresencaChamadaRequestDTO(bruno.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, null, null);

        assertThatThrownBy(() -> chamadaService.gravar(turma.getTurmaId(), dia, List.of(falta(ana), justificadaSemMotivo)))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(chamadaRepository.findByTurmaAndDataChamada(turma, dia)).isEmpty();
        assertThat(presencaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Falta justificada com motivo é gravada e volta com o motivo")
    void faltaJustificadaComMotivo() {
        LocalDate dia = diaSemFeriado(1);
        PresencaChamadaRequestDTO justificada =
                new PresencaChamadaRequestDTO(ana.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, MotivoFalta.ATESTADO, null);

        ChamadaGrupoDTO chamada = chamadaService.gravar(turma.getTurmaId(), dia, List.of(justificada));

        AtendidoChamadaDTO linha = linha(chamada, ana);
        assertThat(linha.statusPresenca()).isEqualTo(StatusPresenca.FALTA_JUSTIFICADA);
        assertThat(linha.motivoFalta()).isEqualTo(MotivoFalta.ATESTADO);
    }

    @Test
    @DisplayName("CA-FT12.6: trocar de grupo deixa um vínculo ativo só")
    void trocaDeGrupoDeixaUmVinculoAtivo() {
        Turma outro = criarGrupo("SI Tarde", DayOfWeek.values());

        vinculoTurmaService.vincular(ana, outro);

        assertThat(turmaAlunosRepository.findByAssistidoAndStatusAndDataSaidaIsNull(ana, StatusGeral.ATIVO))
                .singleElement()
                .extracting(v -> v.getTurma().getTurmaId())
                .isEqualTo(outro.getTurmaId());
    }

    // ---- FT-13: falta justificada só por Atestado ou Suspensão ----

    @Test
    @DisplayName("CA-FT13.2: o suspenso aparece como FJ por Suspensão em todas as chamadas do período, e o envio não troca")
    void suspensoVemComoFaltaJustificada() {
        LocalDate inicio = HOJE.minusDays(3);
        LocalDate fim = HOJE.minusDays(1);
        suspender(ana, inicio, fim);

        for (LocalDate dia : diasComChamada(inicio, fim)) {
            AtendidoChamadaDTO aberta = linha(chamadaService.buscar(turma.getTurmaId(), dia), ana);
            assertThat(aberta.statusPresenca()).isEqualTo(StatusPresenca.FALTA_JUSTIFICADA);
            assertThat(aberta.motivoFalta()).isEqualTo(MotivoFalta.SUSPENSAO);
            assertThat(aberta.suspensoAte()).isEqualTo(fim);

            ChamadaGrupoDTO gravada = chamadaService.gravar(turma.getTurmaId(), dia, List.of(presente(ana), falta(bruno)));
            assertThat(linha(gravada, ana).motivoFalta()).isEqualTo(MotivoFalta.SUSPENSAO);
            assertThat(status(gravada, bruno)).isEqualTo(StatusPresenca.FALTA);
            assertThat(linha(gravada, bruno).suspensoAte()).isNull();
        }
    }

    @Test
    @DisplayName("CA-FT13.3: suspensão retroativa corrige as chamadas já lançadas")
    void suspensaoRetroativaCorrigeChamadas() {
        LocalDate inicio = HOJE.minusDays(3);
        LocalDate fim = HOJE.minusDays(1);
        List<LocalDate> dias = diasComChamada(inicio, fim);
        for (LocalDate dia : dias) {
            chamadaService.gravar(turma.getTurmaId(), dia, List.of(dia.equals(dias.get(0)) ? falta(ana) : presente(ana)));
        }

        suspender(ana, inicio, fim);

        for (LocalDate dia : dias) {
            assertThat(motivoGravado(dia, ana)).isEqualTo(MotivoFalta.SUSPENSAO);
        }
    }

    @Test
    @DisplayName("Encurtar ou arquivar a suspensão devolve as faltas por Suspensão a Falta")
    void encurtarOuArquivarVoltaAFalta() {
        LocalDate inicio = HOJE.minusDays(3);
        LocalDate fim = HOJE.minusDays(1);
        List<LocalDate> dias = diasComChamada(inicio, fim);
        assumeFalse(dias.size() < 2, "precisa de dois dias sem feriado no período");
        dias.forEach(dia -> chamadaService.gravar(turma.getTurmaId(), dia, List.of(presente(ana))));
        Long registroId = suspender(ana, inicio, fim);

        LocalDate ultimo = dias.get(dias.size() - 1);
        disciplinarService.alterarPeriodoSuspensao(registroId, new PeriodoSuspensaoRequestDTO(ultimo, ultimo));
        assertThat(statusGravado(dias.get(0), ana)).isEqualTo(StatusPresenca.FALTA);
        assertThat(motivoGravado(ultimo, ana)).isEqualTo(MotivoFalta.SUSPENSAO);

        disciplinarService.arquivar(registroId, new ArquivarRegistroRequestDTO("Lançado no atendido errado"));
        assertThat(statusGravado(ultimo, ana)).isEqualTo(StatusPresenca.FALTA);
        assertThat(linha(chamadaService.buscar(turma.getTurmaId(), ultimo), ana).suspensoAte()).isNull();
    }

    @Test
    @DisplayName("Suspensão não é escolhida à mão para quem não está suspenso")
    void suspensaoAMaoERecusada() {
        LocalDate dia = diaSemFeriado(1);
        PresencaChamadaRequestDTO aMao =
                new PresencaChamadaRequestDTO(bruno.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, MotivoFalta.SUSPENSAO, null);

        assertThatThrownBy(() -> chamadaService.gravar(turma.getTurmaId(), dia, List.of(aMao)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("use Atestado");
    }

    private Long suspender(Assistido assistido, LocalDate inicio, LocalDate fim) {
        MotivoDisciplinar motivo = motivoDisciplinarRepository.save(
                MotivoDisciplinar.builder().nome("Bullying FT-13").nivel(NivelDisciplinar.SUSPENSAO).build());
        return disciplinarService.registrar(assistido.getAssistidoId(), new RegistroDisciplinarRequestDTO(
                null, null, motivo.getMotivoId(), inicio, "Suspensão de teste", null, inicio, fim)).registro().registroId();
    }

    /** Dias do intervalo que têm chamada (sem feriado nacional). */
    private static List<LocalDate> diasComChamada(LocalDate inicio, LocalDate fim) {
        return inicio.datesUntil(fim.plusDays(1)).filter(dia -> !FeriadosNacionais.ehFeriado(dia)).toList();
    }

    private StatusPresenca statusGravado(LocalDate dia, Assistido assistido) {
        return chamadaRepository.findByTurmaAndDataChamada(turma, dia)
                .flatMap(chamada -> presencaRepository.findByChamadaAndAssistido(chamada, assistido))
                .map(presenca -> presenca.getStatusPresenca())
                .orElse(StatusPresenca.PRESENTE);
    }

    private MotivoFalta motivoGravado(LocalDate dia, Assistido assistido) {
        return chamadaRepository.findByTurmaAndDataChamada(turma, dia)
                .flatMap(chamada -> presencaRepository.findByChamadaAndAssistido(chamada, assistido))
                .flatMap(presenca -> faltaJustificadaRepository.findByPresenca_PresencaId(presenca.getPresencaId()))
                .map(fj -> fj.getMotivoFalta())
                .orElse(null);
    }

    private List<Long> idsNaChamada(LocalDate dia) {
        return chamadaService.buscar(turma.getTurmaId(), dia).atendidos().stream()
                .map(AtendidoChamadaDTO::assistidoId).toList();
    }

    private static StatusPresenca status(ChamadaGrupoDTO chamada, Assistido assistido) {
        return linha(chamada, assistido).statusPresenca();
    }

    private static AtendidoChamadaDTO linha(ChamadaGrupoDTO chamada, Assistido assistido) {
        return chamada.atendidos().stream()
                .filter(a -> a.assistidoId().equals(assistido.getAssistidoId()))
                .findFirst().orElseThrow();
    }

    private static PresencaChamadaRequestDTO falta(Assistido assistido) {
        return new PresencaChamadaRequestDTO(assistido.getAssistidoId(), StatusPresenca.FALTA, null, null);
    }

    private static PresencaChamadaRequestDTO presente(Assistido assistido) {
        return new PresencaChamadaRequestDTO(assistido.getAssistidoId(), StatusPresenca.PRESENTE, null, null);
    }

    /** `diasAtras` dias antes de hoje, recuando mais se cair em feriado nacional (feriado não tem chamada). */
    /** Último dia antes de `limite` que não é feriado nacional. */
    private static LocalDate diaSemFeriadoAntesDe(LocalDate limite) {
        LocalDate dia = limite.minusDays(1);
        while (FeriadosNacionais.ehFeriado(dia)) {
            dia = dia.minusDays(1);
        }
        return dia;
    }

    private static LocalDate diaSemFeriado(int diasAtras) {
        LocalDate dia = HOJE.minusDays(diasAtras);
        while (FeriadosNacionais.ehFeriado(dia)) {
            dia = dia.minusDays(1);
        }
        return dia;
    }

    private Turma criarGrupo(String nome, DayOfWeek... dias) {
        Turma grupo = new Turma();
        grupo.setNomeTurma(nome);
        grupo.setTurno(Turno.MANHA);
        grupo.setCapacidade(20);
        grupo.setDiasDaSemana(new ArrayList<>(List.of(dias)));
        return turmaRepository.save(grupo);
    }

    private Assistido matricular(String nome, Turma grupo, LocalDate entrada, LocalDate saida) {
        Assistido assistido = assistidoRepository.save(Assistido.builder()
                .nomeCompleto(nome)
                .dataNascimento(LocalDate.of(2015, 1, 1))
                .dataEntrada(entrada)
                .status(StatusGeral.ATIVO)
                .turma(grupo)
                .build());
        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(grupo);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(entrada);
        vinculo.setDataSaida(saida);
        vinculo.setStatus(saida == null ? StatusGeral.ATIVO : StatusGeral.INATIVO);
        turmaAlunosRepository.save(vinculo);
        return assistido;
    }

    private void voltarAoGrupo(Assistido assistido, LocalDate data) {
        TurmaAlunos volta = new TurmaAlunos();
        volta.setTurma(turma);
        volta.setAssistido(assistido);
        volta.setDataEntrada(data);
        volta.setStatus(StatusGeral.ATIVO);
        turmaAlunosRepository.save(volta);
    }

    private Usuario criarUsuario(NomeNivelPermissao perfil, String email) {
        NivelPermissao nivel = nivelPermissaoRepository.save(NivelPermissao.builder().nome(perfil).build());
        return usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivel)
                .nomeCompleto("Usuário " + perfil.name())
                .email(email)
                .build());
    }

    private static void autenticarComo(Usuario usuario) {
        Jwt jwt = Jwt.withTokenValue("token-fake-de-teste-" + usuario.getUsuarioId())
                .header("alg", "none")
                .subject(usuario.getUsuarioId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("perfil", usuario.getNivelPermissao().getNome().name())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getNivelPermissao().getNome().name()))));
    }
}
