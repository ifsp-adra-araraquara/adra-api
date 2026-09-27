package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * US-67: Coordenador corrige chamada de qualquer data; Sociopedagógico só
 * corrige a chamada do dia atual. Cobre os 4 cenários do card (2 perfis x
 * 2 datas), service-a-service, no mesmo estilo de ChamadaFluxoDesligamentoIT.
 */
@SpringBootTest
@Testcontainers
@Import(PresencaCorrecaoPorDataIT.ContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.brevo.api-key=dummy-teste",
        "adra.brevo.remetente-email=teste@teste.local",
        "adra.brevo.remetente-nome=Teste",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
@Transactional
class PresencaCorrecaoPorDataIT {

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
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private AulaRepository aulaRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private PresencaService presencaService;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();

    private Usuario coordenador;
    private Usuario sociopedagogico;
    private Assistido assistido;
    private Aula aulaHoje;
    private Aula aulaSemanaPassada;
    private Long presencaHojeId;
    private Long presencaSemanaPassadaId;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        }

        NivelPermissao nivelCoordenador = nivelPermissaoRepository.save(
                NivelPermissao.builder().nome(NomeNivelPermissao.COORDENADOR).build());
        NivelPermissao nivelSocio = nivelPermissaoRepository.save(
                NivelPermissao.builder().nome(NomeNivelPermissao.SOCIOPEDAGOGICO).build());

        coordenador = usuarioRepository.save(
                Usuario.builder()
                        .nivelPermissao(nivelCoordenador)
                        .nomeCompleto("Coordenadora Teste")
                        .email("coordenadora-us67@teste.local")
                        .build());
        sociopedagogico = usuarioRepository.save(
                Usuario.builder()
                        .nivelPermissao(nivelSocio)
                        .nomeCompleto("Sociopedagoga Teste")
                        .email("socio-us67@teste.local")
                        .build());

        autenticarComo(coordenador);

        Oficina oficina = new Oficina();
        oficina.setNomeOficina("Oficina Teste US-67");
        oficina.setOficineiroResponsavel(coordenador);
        oficina = oficinaRepository.save(oficina);

        Turma turma = new Turma();
        turma.setOficina(oficina);
        turma.setOficineiroResponsavel(coordenador);
        turma.setNomeTurma("Turma US-67");
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma = turmaRepository.save(turma);

        assistido = Assistido.builder()
                .nomeCompleto("Assistido US-67")
                .dataNascimento(LocalDate.of(2015, 1, 1))
                .dataEntrada(HOJE.minusMonths(6))
                .status(StatusGeral.ATIVO)
                .turma(turma)
                .build();
        assistido = assistidoRepository.save(assistido);

        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(turma);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(HOJE.minusMonths(6));
        vinculo.setStatus(StatusGeral.ATIVO);
        turmaAlunosRepository.save(vinculo);

        aulaHoje = criarAulaRealizada(turma, HOJE);
        aulaSemanaPassada = criarAulaRealizada(turma, HOJE.minusDays(7));

        // lançamento inicial (como coordenador) — a correção é testada em cima destes registros
        presencaHojeId = presencaService.registrarPresenca(
                new PresencaRequestDTO(aulaHoje.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA, null, null)).presencaId();
        presencaSemanaPassadaId = presencaService.registrarPresenca(
                new PresencaRequestDTO(aulaSemanaPassada.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA, null, null)).presencaId();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> cenarios() {
        return Stream.of(
                arguments(NomeNivelPermissao.COORDENADOR, "hoje", true),
                arguments(NomeNivelPermissao.COORDENADOR, "semana_passada", true),
                arguments(NomeNivelPermissao.SOCIOPEDAGOGICO, "hoje", true),
                arguments(NomeNivelPermissao.SOCIOPEDAGOGICO, "semana_passada", false)
        );
    }

    @ParameterizedTest(name = "{0} corrige chamada de \"{1}\" -> permitido={2}")
    @MethodSource("cenarios")
    @DisplayName("CA-67.1/CA-67.2: regra de correção por data cruzada com o perfil")
    void corrigirChamadaPorPerfilEData(NomeNivelPermissao perfil, String data, boolean devePermitir) {
        Usuario usuarioDoTeste = perfil == NomeNivelPermissao.COORDENADOR ? coordenador : sociopedagogico;
        autenticarComo(usuarioDoTeste);

        Long presencaId = data.equals("hoje") ? presencaHojeId : presencaSemanaPassadaId;
        Long aulaId = data.equals("hoje") ? aulaHoje.getAulaId() : aulaSemanaPassada.getAulaId();

        PresencaRequestDTO correcao = new PresencaRequestDTO(
                aulaId, assistido.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, MotivoFalta.SAUDE, null);

        if (devePermitir) {
            assertThatCode(() -> presencaService.atualizarPresenca(presencaId, correcao))
                    .doesNotThrowAnyException();

            List<PresencaResponseDTO> chamada = presencaService.buscarPorAula(aulaId);
            assertThat(chamada)
                    .filteredOn(p -> p.assistidoId().equals(assistido.getAssistidoId()))
                    .first()
                    .satisfies(p -> assertThat(p.statusPresenca()).isEqualTo(StatusPresenca.FALTA_JUSTIFICADA));
        } else {
            assertThatThrownBy(() -> presencaService.atualizarPresenca(presencaId, correcao))
                    .isInstanceOf(AcessoNegadoException.class);
        }
    }

    private Aula criarAulaRealizada(Turma turma, LocalDate data) {
        Aula aula = new Aula();
        aula.setTurma(turma);
        aula.setDataAula(data);
        aula.setStatusAula(StatusAula.REALIZADA);
        return aulaRepository.save(aula);
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
