package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
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

/**
 * Fluxo de ponta a ponta do CA-70, service-a-service (sem HTTP/auth real),
 * contra Postgres real via Testcontainers — a mesma ideia de
 * TestePresencaManual (ferramentas/), só que automatizado e rodando local,
 * NUNCA contra o banco de stage/produção. Cobre exatamente os 4 "Testes de
 * Fluxo do Usuário" do card:
 *  1. Desliga assistido hoje -> aula de amanhã não mostra ele
 *  2. Aula da semana passada (antes da saída) continua mostrando ele + a
 *     presença antiga lançada continua íntegra
 *  3. Lançar chamada pra ele numa aula futura via chamada direta é rejeitado
 *  4. Reativa o assistido -> volta a aparecer nas aulas futuras
 */
@SpringBootTest
@Testcontainers
@Import(ChamadaFluxoDesligamentoIT.ContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.brevo.api-key=dummy-teste",
        "adra.brevo.remetente-email=teste@teste.local",
        "adra.brevo.remetente-nome=Teste",
        "adra.frontend.url=http://localhost:4200",
        // igual ao "?stringtype=unspecified" da URL real (application-local/
        // stage.properties) — sem isso, o driver manda o "ip" nulo de
        // LogAuditoria como varchar e o Postgres rejeita (coluna é inet).
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
@Transactional // 2+ testes no mesmo container Testcontainers — rollback entre eles evita choque de unique (ex.: nivel_permissao.nome)
class ChamadaFluxoDesligamentoIT {

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
    @Autowired private AssistidoService assistidoService;
    @Autowired private PresencaService presencaService;
    @Autowired private DataSource dataSource;

    private Turma turma;
    private Assistido assistido;
    private Aula aulaSemanaPassada;
    private Aula aulaAmanha;

    private static final LocalDate HOJE = LocalDate.now();

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
        Usuario coordenador = usuarioRepository.save(
                Usuario.builder()
                        .nivelPermissao(nivelCoordenador)
                        .nomeCompleto("Coordenadora Teste")
                        .email("coordenadora@teste.local")
                        .build());
        autenticarComo(coordenador);

        Usuario oficineiro = usuarioRepository.save(
                Usuario.builder()
                        .nivelPermissao(nivelCoordenador)
                        .nomeCompleto("Oficineiro Teste")
                        .email("oficineiro@teste.local")
                        .build());
        Oficina oficina = new Oficina();
        oficina.setNomeOficina("Oficina Teste");
        oficina.setOficineiroResponsavel(oficineiro);
        oficina = oficinaRepository.save(oficina);

        turma = new Turma();
        turma.setOficina(oficina);
        turma.setOficineiroResponsavel(oficineiro);
        turma.setNomeTurma("Turma Fluxo CA-70");
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma = turmaRepository.save(turma);

        assistido = Assistido.builder()
                .nomeCompleto("Assistido Fluxo")
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

        aulaSemanaPassada = criarAulaRealizada(HOJE.minusDays(7));
        aulaAmanha = criarAulaRealizada(HOJE.plusDays(1));

        // presença lançada ANTES da saída, na aula da semana passada — é o
        // histórico que o CA-70.2 promete não apagar.
        presencaService.registrarChamada(aulaSemanaPassada.getAulaId(), List.of(
                new PresencaRequestDTO(aulaSemanaPassada.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA, null, null)
        ));
    }

    @Test
    @DisplayName("Fluxo completo do CA-70: desligar hoje, some de amanhã, preserva histórico, rejeita lançamento futuro, reativar traz de volta")
    void fluxoCompletoDesligamentoEReativacao() {
        // --- 1) desliga o assistido com data_saida = hoje ---
        assistidoService.alterarStatus(assistido.getAssistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, HOJE, "Mudou de cidade"));

        // --- 2) aula de amanhã não mostra mais ele (CA-70.1) ---
        List<PresencaResponseDTO> chamadaAmanha = presencaService.buscarPorAula(aulaAmanha.getAulaId());
        assertThat(chamadaAmanha).extracting(PresencaResponseDTO::assistidoId)
                .doesNotContain(assistido.getAssistidoId());

        // --- 3) aula da semana passada continua mostrando ele, com a falta preservada (CA-70.2) ---
        List<PresencaResponseDTO> chamadaSemanaPassada = presencaService.buscarPorAula(aulaSemanaPassada.getAulaId());
        assertThat(chamadaSemanaPassada)
                .filteredOn(p -> p.assistidoId().equals(assistido.getAssistidoId()))
                .hasSize(1)
                .first()
                .satisfies(p -> assertThat(p.statusPresenca()).isEqualTo(StatusPresenca.FALTA));

        // --- 4) lançar chamada dele na aula futura via API é rejeitado (CA-70.3) ---
        assertThatThrownBy(() -> presencaService.registrarPresenca(
                new PresencaRequestDTO(aulaAmanha.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA, null, null)))
                .isInstanceOf(RegraNegocioException.class);

        // --- 5) reativa -> volta a aparecer na aula futura (CA-70.4) ---
        assistidoService.alterarStatus(assistido.getAssistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.ATIVO, null, null));

        List<PresencaResponseDTO> chamadaAmanhaDepoisDeReativar = presencaService.buscarPorAula(aulaAmanha.getAulaId());
        assertThat(chamadaAmanhaDepoisDeReativar).extracting(PresencaResponseDTO::assistidoId)
                .contains(assistido.getAssistidoId());
    }

    @Test
    @DisplayName("CA-70.3: corrigir (PUT) uma presença já existente de assistido desligado numa aula posterior à saída também é rejeitado")
    void corrigirPresencaDeDesligadoTambemERejeitado() {
        // presença lançada ENQUANTO ainda ativo, numa aula futura
        PresencaResponseDTO presencaCriada = presencaService.registrarPresenca(
                new PresencaRequestDTO(aulaAmanha.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA, null, null));

        // desliga com data_saida = hoje (antes da aula de amanhã)
        assistidoService.alterarStatus(assistido.getAssistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, HOJE, "Mudou de cidade"));

        assertThatThrownBy(() -> presencaService.atualizarPresenca(presencaCriada.presencaId(),
                new PresencaRequestDTO(aulaAmanha.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA_JUSTIFICADA, adra.ifsp.edu.br.api.domain.enums.MotivoFalta.OUTRO, "tentativa")))
                .isInstanceOf(RegraNegocioException.class);
    }

    private Aula criarAulaRealizada(LocalDate data) {
        Aula aula = new Aula();
        aula.setTurma(turma);
        aula.setDataAula(data);
        aula.setStatusAula(StatusAula.REALIZADA);
        return aulaRepository.save(aula);
    }

    private static void autenticarComo(Usuario usuario) {
        Jwt jwt = Jwt.withTokenValue("token-fake-de-teste")
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
