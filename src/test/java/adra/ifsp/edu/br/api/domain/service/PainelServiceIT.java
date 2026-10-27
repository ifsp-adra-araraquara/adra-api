package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.painel.PainelHojeDTO;
import adra.ifsp.edu.br.api.domain.dto.painel.PainelMensalDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.MotivoDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.web.controller.PainelController;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * FT-05: números do Painel do Coordenador com um cenário pequeno e
 * conhecido — confere a regra de frequência (presença esparsa, FJ como
 * ausência, desligado fora), pendências, faltas seguidas, progressão e acesso.
 */
@SpringBootTest
@Testcontainers
@Import(PainelServiceIT.ContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
@Transactional
class PainelServiceIT {

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
                    .withInitScript("create-schema-adra.sql");
        }
    }

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private AulaRepository aulaRepository;
    @Autowired private MotivoDisciplinarRepository motivoRepository;
    @Autowired private PresencaService presencaService;
    @Autowired private DisciplinarService disciplinarService;
    @Autowired private PainelService painelService;
    @Autowired private PainelController painelController;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();
    /** Mês fechado, para as datas das aulas não dependerem do dia em que o teste roda. */
    private static final YearMonth MES = YearMonth.from(HOJE).minusMonths(1);

    private Usuario coordenador;
    private Usuario sociopedagogico;
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

        coordenador = criarUsuario(NomeNivelPermissao.COORDENADOR, "coord-ft05@teste.local");
        sociopedagogico = criarUsuario(NomeNivelPermissao.SOCIOPEDAGOGICO, "socio-ft05@teste.local");
        autenticarComo(coordenador);

        Oficina oficina = new Oficina();
        oficina.setNomeOficina("Futebol");
        oficina.setOficineiroResponsavel(coordenador);
        oficina = oficinaRepository.save(oficina);

        turma = new Turma();
        turma.setOficina(oficina);
        turma.setOficineiroResponsavel(coordenador);
        turma.setNomeTurma("Turma A - Futebol");
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma = turmaRepository.save(turma);

        LocalDate entrada = MES.atDay(1).minusMonths(3);
        ana = matricular("Ana Painel", LocalDate.of(2015, 3, 3), entrada, null);
        bruno = matricular("Bruno Painel", HOJE.plusDays(2).minusYears(10), entrada, null);
        // Desligada antes do mês: não pode entrar em frequência nem em ativos.
        matricular("Carla Desligada", LocalDate.of(2012, 1, 1), entrada, MES.atDay(1).minusDays(1));
    }

    @Test
    @DisplayName("Frequência do mês: presença esparsa, FJ como ausência e desligado fora")
    void frequenciaDoMes() {
        Aula a1 = aula(MES.atDay(5), StatusAula.REALIZADA);
        aula(MES.atDay(12), StatusAula.REALIZADA);
        Aula a3 = aula(MES.atDay(19), StatusAula.REALIZADA);
        aula(MES.atDay(26), StatusAula.REALIZADA);
        aula(MES.atDay(27), StatusAula.PLANEJADA); // chamada esquecida
        falta(a1, ana, StatusPresenca.FALTA);
        falta(a3, bruno, StatusPresenca.FALTA_JUSTIFICADA);

        PainelMensalDTO painel = mensal();

        // 4 aulas × 2 alunos = 8 esperados; 2 ausências -> 75%
        assertThat(painel.frequencia().percentual()).isEqualTo(75);
        assertThat(painel.frequencia().aulasRealizadas()).isEqualTo(4);
        assertThat(painel.frequencia().chamadasPendentes()).isEqualTo(1);
        assertThat(painel.frequencia().faltas()).isEqualTo(1);
        assertThat(painel.frequencia().faltasJustificadas()).isEqualTo(1);
        assertThat(painel.turmas()).singleElement()
                .extracting(PainelMensalDTO.Turma::frequencia, PainelMensalDTO.Turma::alunos, PainelMensalDTO.Turma::nomeOficina)
                .containsExactly(75, 2L, "Futebol");
        assertThat(painel.ocupacao().percentual()).isEqualTo(10);
        assertThat(painel.assistidos().ativos()).isEqualTo(2);
        assertThat(painel.tendencia()).hasSize(PainelService.MESES_TENDENCIA);
        assertThat(painel.tendencia().getLast()).extracting(PainelMensalDTO.FrequenciaMes::percentual).isEqualTo(75);
        assertThat(painel.frequencia().percentualMesAnterior()).isNull();
    }

    @Test
    @DisplayName("Hoje: 3 faltas seguidas, chamada atrasada, um passo da advertência e aniversariante")
    void blocoDeHoje() {
        Aula d3 = aula(HOJE.minusDays(3), StatusAula.REALIZADA);
        Aula d2 = aula(HOJE.minusDays(2), StatusAula.REALIZADA);
        Aula d1 = aula(HOJE.minusDays(1), StatusAula.REALIZADA);
        Aula pendente = aula(HOJE.minusDays(5), StatusAula.PLANEJADA);
        falta(d3, ana, StatusPresenca.FALTA);
        falta(d2, ana, StatusPresenca.FALTA);
        falta(d1, ana, StatusPresenca.FALTA);
        // Bruno: FJ no meio quebra a sequência
        falta(d2, bruno, StatusPresenca.FALTA);
        falta(d1, bruno, StatusPresenca.FALTA_JUSTIFICADA);

        MotivoDisciplinar palavrao = motivoRepository.save(
                MotivoDisciplinar.builder().nome("Palavrão").nivel(NivelDisciplinar.OCORRENCIA).build());
        for (int i = 0; i < 2; i++) {
            disciplinarService.registrar(ana.getAssistidoId(),
                    new RegistroDisciplinarRequestDTO(palavrao.getMotivoId(), HOJE, "Fato", null, null, null));
        }
        entityManager.flush();

        PainelHojeDTO hoje = painelService.hoje();

        assertThat(hoje.faltasSeguidas().itens())
                .extracting(PainelHojeDTO.AlunoFaltas::assistidoId, PainelHojeDTO.AlunoFaltas::faltas)
                .containsExactly(tuple(ana.getAssistidoId(), 3L));
        assertThat(hoje.chamadasPendentes().total()).isEqualTo(1);
        assertThat(hoje.chamadasPendentes().itens()).extracting(PainelHojeDTO.ChamadaPendente::aulaId)
                .containsExactly(pendente.getAulaId());
        assertThat(hoje.umPassoDaProgressao().itens())
                .extracting(PainelHojeDTO.UmPasso::assistidoId, PainelHojeDTO.UmPasso::nivel)
                .containsExactly(tuple(ana.getAssistidoId(), "OCORRENCIA"));
        assertThat(hoje.aniversariantes())
                .extracting(PainelHojeDTO.Aniversariante::assistidoId, PainelHojeDTO.Aniversariante::idade)
                .containsExactly(tuple(bruno.getAssistidoId(), 10));
    }

    @Test
    @DisplayName("Disciplina do mês conta só registros do mês, por nível")
    void disciplinaDoMes() {
        MotivoDisciplinar agressao = motivoRepository.save(
                MotivoDisciplinar.builder().nome("Agressão").nivel(NivelDisciplinar.ADVERTENCIA).build());
        disciplinarService.registrar(bruno.getAssistidoId(),
                new RegistroDisciplinarRequestDTO(agressao.getMotivoId(), MES.atDay(10), "Fato", null, null, null));
        disciplinarService.registrar(bruno.getAssistidoId(),
                new RegistroDisciplinarRequestDTO(agressao.getMotivoId(), MES.atDay(1).minusDays(3), "Fato", null, null, null));

        PainelMensalDTO painel = mensal();

        assertThat(painel.disciplina().advertencias()).isEqualTo(1);
        assertThat(painel.disciplina().total()).isEqualTo(1);
        assertThat(painel.disciplina().totalMesAnterior()).isEqualTo(1);
    }

    @Test
    @DisplayName("Filtro de oficina: outra oficina não entra em frequência, turmas nem ativos")
    void filtroDeOficina() {
        Oficina teatro = new Oficina();
        teatro.setNomeOficina("Teatro");
        teatro.setOficineiroResponsavel(coordenador);
        teatro = oficinaRepository.save(teatro);
        aula(MES.atDay(5), StatusAula.REALIZADA);

        entityManager.flush();
        PainelMensalDTO futebol = painelService.mensal(MES, turma.getOficina().getOficinaId());
        PainelMensalDTO soTeatro = painelService.mensal(MES, teatro.getOficinaId());

        assertThat(futebol.turmas()).hasSize(1);
        assertThat(soTeatro.turmas()).isEmpty();
        assertThat(soTeatro.frequencia().percentual()).isNull();
        assertThat(soTeatro.assistidos().ativos()).isZero();
        assertThat(futebol.assistidos().ativos()).isEqualTo(2);
    }

    @Test
    @DisplayName("Atividade recente traz chamada lançada e assistido cadastrado, sem texto livre")
    void atividadeRecente() {
        aula(HOJE.minusDays(1), StatusAula.REALIZADA);
        entityManager.flush();

        assertThat(painelService.hoje().atividades())
                .extracting(PainelHojeDTO.Atividade::tipo)
                .contains("CHAMADA", "ASSISTIDO")
                .hasSizeLessThanOrEqualTo(PainelService.LIMITE_ATIVIDADES);
    }

    @Test
    @DisplayName("Só o Coordenador acessa o painel")
    void somenteCoordenador() {
        autenticarComo(sociopedagogico);
        assertThatThrownBy(() -> painelController.hoje()).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> painelController.mensal(MES, null)).isInstanceOf(AuthorizationDeniedException.class);
    }

    private PainelMensalDTO mensal() {
        entityManager.flush(); // o painel lê por JDBC, fora do Hibernate
        return painelService.mensal(MES, null);
    }

    private Assistido matricular(String nome, LocalDate nascimento, LocalDate entrada, LocalDate saida) {
        Assistido assistido = assistidoRepository.save(Assistido.builder()
                .nomeCompleto(nome)
                .dataNascimento(nascimento)
                .dataEntrada(entrada)
                .dataSaida(saida)
                .status(saida == null ? StatusGeral.ATIVO : StatusGeral.INATIVO)
                .turma(turma)
                .build());
        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(turma);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(entrada);
        vinculo.setDataSaida(saida);
        vinculo.setStatus(saida == null ? StatusGeral.ATIVO : StatusGeral.INATIVO);
        turmaAlunosRepository.save(vinculo);
        return assistido;
    }

    private Aula aula(LocalDate data, StatusAula status) {
        Aula aula = new Aula();
        aula.setTurma(turma);
        aula.setDataAula(data);
        aula.setStatusAula(status);
        return aulaRepository.save(aula);
    }

    private void falta(Aula aula, Assistido assistido, StatusPresenca status) {
        presencaService.registrarPresenca(new PresencaRequestDTO(aula.getAulaId(), assistido.getAssistidoId(), status,
                status == StatusPresenca.FALTA_JUSTIFICADA ? MotivoFalta.SAUDE : null, null));
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

        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getNivelPermissao().getNome().name())));

        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
