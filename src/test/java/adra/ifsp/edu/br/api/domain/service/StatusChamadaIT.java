package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.aula.AulaStatusChamadaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaStatusPatchRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusChamada;
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
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * US-71: painel de status de chamada do coordenador. Cobre os testes de
 * fluxo do card (2 de 3 aulas lançadas, aula cancelada some do painel)
 * mais o filtro por turma/período, no mesmo estilo de PresencaCorrecaoPorDataIT.
 */
@SpringBootTest
@Testcontainers
@Import(StatusChamadaIT.ContainerConfig.class)
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
class StatusChamadaIT {

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
    @Autowired private AulaService aulaService;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();

    private Usuario coordenador;
    private Oficina oficina;
    private Turma turma;
    private Assistido assistido;
    private Aula aula1;
    private Aula aula2;
    private Aula aula3;

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

        coordenador = usuarioRepository.save(
                Usuario.builder()
                        .nivelPermissao(nivelCoordenador)
                        .nomeCompleto("Coordenadora Teste")
                        .email("coordenadora-us71@teste.local")
                        .build());

        autenticarComo(coordenador);

        oficina = new Oficina();
        oficina.setNomeOficina("Oficina Teste US-71");
        oficina.setOficineiroResponsavel(coordenador);
        oficina = oficinaRepository.save(oficina);

        turma = criarTurma("Turma US-71");

        assistido = Assistido.builder()
                .nomeCompleto("Assistido US-71")
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

        // 3 aulas no período; a chamada é lançada em 2 (o fluxo do front
        // marca REALIZADA antes de lançar — a 3ª fica PLANEJADA, esquecida)
        aula1 = criarAula(turma, HOJE.minusDays(14), StatusAula.REALIZADA);
        aula2 = criarAula(turma, HOJE.minusDays(7), StatusAula.REALIZADA);
        aula3 = criarAula(turma, HOJE.minusDays(3), StatusAula.PLANEJADA);

        lancarChamada(aula1);
        lancarChamada(aula2);
    }

    @Test
    @DisplayName("CA-71.1: 2 de 3 aulas com chamada -> 2 LANCADA e 1 PENDENTE")
    void mostraStatusDeLancamentoPorAula() {
        List<AulaStatusChamadaResponseDTO> painel =
                aulaService.listarStatusChamada(turma.getTurmaId(), HOJE.minusDays(30), HOJE);

        assertThat(painel)
                .extracting(AulaStatusChamadaResponseDTO::aulaId, AulaStatusChamadaResponseDTO::statusChamada)
                .containsExactly(
                        tuple(aula1.getAulaId(), StatusChamada.LANCADA),
                        tuple(aula2.getAulaId(), StatusChamada.LANCADA),
                        tuple(aula3.getAulaId(), StatusChamada.PENDENTE)
                );
    }

    @Test
    @DisplayName("CA-71.3: aula cancelada some do painel de pendências")
    void aulaCanceladaNaoApareceComoPendente() {
        aulaService.atualizarStatus(aula3.getAulaId(), new AulaStatusPatchRequestDTO(StatusAula.CANCELADA));
        criarAula(turma, HOJE.minusDays(1), StatusAula.REMARCADA);

        List<AulaStatusChamadaResponseDTO> painel =
                aulaService.listarStatusChamada(turma.getTurmaId(), HOJE.minusDays(30), HOJE);

        assertThat(painel).extracting(AulaStatusChamadaResponseDTO::aulaId)
                .containsExactly(aula1.getAulaId(), aula2.getAulaId());
        assertThat(painel).noneMatch(a -> a.statusChamada() == StatusChamada.PENDENTE);
    }

    @Test
    @DisplayName("CA-71.3: aula futura ainda não conta como pendente")
    void aulaFuturaNaoApareceComoPendente() {
        criarAula(turma, HOJE.plusDays(2), StatusAula.PLANEJADA);

        List<AulaStatusChamadaResponseDTO> painel =
                aulaService.listarStatusChamada(turma.getTurmaId(), HOJE.minusDays(30), HOJE.plusDays(30));

        assertThat(painel).hasSize(3);
    }

    @Test
    @DisplayName("CA-71.2: filtro por turma e por período")
    void filtraPorTurmaEPeriodo() {
        Turma outraTurma = criarTurma("Outra turma US-71");
        Aula aulaOutraTurma = criarAula(outraTurma, HOJE.minusDays(7), StatusAula.PLANEJADA);

        assertThat(aulaService.listarStatusChamada(outraTurma.getTurmaId(), null, null))
                .extracting(AulaStatusChamadaResponseDTO::aulaId)
                .containsExactly(aulaOutraTurma.getAulaId());

        assertThat(aulaService.listarStatusChamada(turma.getTurmaId(), HOJE.minusDays(8), HOJE.minusDays(2)))
                .extracting(AulaStatusChamadaResponseDTO::aulaId)
                .containsExactly(aula2.getAulaId(), aula3.getAulaId());
    }

    @Test
    @DisplayName("Período invertido é rejeitado")
    void periodoInvertidoERejeitado() {
        assertThatThrownBy(() -> aulaService.listarStatusChamada(null, HOJE, HOJE.minusDays(1)))
                .isInstanceOf(RegraNegocioException.class);
    }

    private Turma criarTurma(String nome) {
        Turma nova = new Turma();
        nova.setOficina(oficina);
        nova.setOficineiroResponsavel(coordenador);
        nova.setNomeTurma(nome);
        nova.setTurno(Turno.MANHA);
        nova.setCapacidade(20);
        return turmaRepository.save(nova);
    }

    private Aula criarAula(Turma turmaDaAula, LocalDate data, StatusAula status) {
        Aula aula = new Aula();
        aula.setTurma(turmaDaAula);
        aula.setDataAula(data);
        aula.setStatusAula(status);
        return aulaRepository.save(aula);
    }

    private void lancarChamada(Aula aula) {
        presencaService.registrarPresenca(
                new PresencaRequestDTO(aula.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.PRESENTE, null, null));
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
