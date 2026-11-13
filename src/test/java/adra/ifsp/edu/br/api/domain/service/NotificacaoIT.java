package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.notificacao.NotificacaoDTO;
import adra.ifsp.edu.br.api.domain.dto.notificacao.ResultadoProcessamentoDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.enums.TipoEnvolvido;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaTurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FT-14: o teste de fluxo do card, contra Postgres real (Testcontainers). */
@SpringBootTest
@Testcontainers
@Import(ContainerPostgresAdra.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified",
        "adra.notificacao.email.ligado=true"
})
@Transactional
class NotificacaoIT {

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private ChamadaRepository chamadaRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private OficinaTurmaRepository oficinaTurmaRepository;
    @Autowired private NotificacaoJob notificacaoJob;
    @Autowired private NotificacaoService notificacaoService;
    @Autowired private DataSource dataSource;

    @MockitoBean private JavaMailSender mailSender;

    private static final LocalDate HOJE = LocalDate.now();

    private Usuario coordenador;
    private Usuario educadorSocial;
    private Turma turma;
    private Assistido ana;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
        }
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));

        coordenador = criarUsuario(NomeNivelPermissao.COORDENADOR, "coord-ft14@teste.local");
        educadorSocial = criarUsuario(NomeNivelPermissao.EDUCADOR_SOCIAL, "educador-ft14@teste.local");

        turma = new Turma();
        turma.setNomeTurma("SI Manhã");
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma.setDiasDaSemana(new ArrayList<>(List.of(HOJE.getDayOfWeek())));
        turma = turmaRepository.save(turma);
        ana = matricular("Ana Notificada", HOJE.minusMonths(3));
    }

    @Test
    @DisplayName("Fluxo do card: 3 faltas seguidas → um e-mail ao Coordenador; rodar de novo não manda outro")
    void tresFaltasUmEmail() throws Exception {
        faltar(HOJE.minusDays(3));
        faltar(HOJE.minusDays(2));
        faltar(HOJE.minusDays(1));

        ResultadoProcessamentoDTO primeira = notificacaoJob.processar();

        assertThat(primeira.geradas()).isEqualTo(1);
        assertThat(primeira.mensagensEnviadas()).isEqualTo(1);
        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(enviado.capture());
        MimeMessage email = enviado.getValue();
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo(coordenador.getEmail());
        assertThat((String) email.getContent()).contains("Ana Notificada chegou a 3 faltas seguidas");

        // CA-FT14.2: nada novo, nada sai.
        ResultadoProcessamentoDTO segunda = notificacaoJob.processar();
        assertThat(segunda.geradas()).isZero();
        assertThat(segunda.mensagensEnviadas()).isZero();
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        // No sino do Coordenador, uma notificação que aponta a frequência da Ana.
        autenticarComo(coordenador);
        List<NotificacaoDTO> minhas = notificacaoService.minhas(0, 20).conteudo();
        assertThat(minhas).singleElement().satisfies(n -> {
            assertThat(n.tipo()).isEqualTo(TipoAviso.FALTAS_SEGUIDAS);
            assertThat(n.lida()).isFalse();
            assertThat(n.link()).isEqualTo("/assistidos?assistidoId=" + ana.getAssistidoId() + "&aba=cham");
            assertThat(n.envolvidos()).singleElement()
                    .isEqualTo(new NotificacaoDTO.EnvolvidoDTO(TipoEnvolvido.ASSISTIDO, ana.getAssistidoId()));
        });
        assertThat(notificacaoService.naoLidas()).isEqualTo(1);
        notificacaoService.marcarComoLida(minhas.getFirst().notificacaoId());
        assertThat(notificacaoService.naoLidas()).isZero();

        // O Educador Social não recebe o aviso de faltas.
        autenticarComo(educadorSocial);
        assertThat(notificacaoService.minhas(0, 20).conteudo()).isEmpty();
    }

    @Test
    @DisplayName("A 4ª falta da mesma sequência não avisa de novo")
    void mesmaSequenciaNaoRepete() {
        faltar(HOJE.minusDays(4));
        faltar(HOJE.minusDays(3));
        faltar(HOJE.minusDays(2));
        notificacaoJob.processar();

        faltar(HOJE.minusDays(1));
        assertThat(notificacaoJob.processar().geradas()).isZero();
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Sem falta seguida e sem semanário pendente, nenhum e-mail")
    void semAvisoSemEmail() {
        faltar(HOJE.minusDays(2));
        faltar(HOJE.minusDays(1));

        assertThat(notificacaoJob.processar().geradas()).isZero();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Semanário pendente: lembrete ao oficineiro uma vez por semana")
    void lembreteDeSemanario() {
        Usuario oficineiro = criarUsuario(NomeNivelPermissao.OFICINEIRO, "oficineiro-ft14@teste.local");
        Oficina futebol = new Oficina();
        futebol.setNomeOficina("Futebol");
        futebol.setOficineiroResponsavel(oficineiro);
        futebol = oficinaRepository.save(futebol);
        oficinaTurmaRepository.save(new OficinaTurma(futebol, turma));

        notificacaoJob.processar();
        notificacaoJob.processar();

        autenticarComo(oficineiro);
        assertThat(notificacaoService.minhas(0, 20).conteudo())
                .singleElement()
                .satisfies(n -> assertThat(n.titulo()).isEqualTo("Lembrete: semanário de Futebol"));
    }

    private Assistido matricular(String nome, LocalDate entrada) {
        Assistido assistido = assistidoRepository.save(Assistido.builder()
                .nomeCompleto(nome)
                .dataNascimento(LocalDate.of(2015, 3, 3))
                .dataEntrada(entrada)
                .status(StatusGeral.ATIVO)
                .turma(turma)
                .build());
        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(turma);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(entrada);
        vinculo.setStatus(StatusGeral.ATIVO);
        turmaAlunosRepository.save(vinculo);
        return assistido;
    }

    private void faltar(LocalDate data) {
        Chamada chamada = new Chamada();
        chamada.setTurma(turma);
        chamada.setDataChamada(data);
        chamada.setCriadoPor(coordenador);
        chamada.setAtualizadoPor(coordenador);
        chamada = chamadaRepository.save(chamada);

        Presenca presenca = new Presenca();
        presenca.setChamada(chamada);
        presenca.setAssistido(ana);
        presenca.setStatusPresenca(StatusPresenca.FALTA);
        presenca.setCriadoPor(coordenador);
        presenca.setAtualizadoPor(coordenador);
        presencaRepository.save(presenca);
    }

    private Usuario criarUsuario(NomeNivelPermissao perfil, String email) {
        NivelPermissao nivel = nivelPermissaoRepository.findByNome(perfil)
                .orElseGet(() -> nivelPermissaoRepository.save(NivelPermissao.builder().nome(perfil).build()));
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
