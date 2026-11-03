package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.aviso.AvisoDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.GerarPelaRecomendacaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.MotivoDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** FT-18: central de avisos por perfil, contra Postgres real (Testcontainers). */
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
class AvisoServiceIT {

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private MotivoDisciplinarRepository motivoRepository;
    @Autowired private DisciplinarService disciplinarService;
    @Autowired private AvisoService avisoService;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();

    private Usuario coordenador;
    private Usuario educadorSocial;
    private Usuario assistenteSocial;
    private Usuario oficineiro;
    private Assistido assistido;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
            // Como na migration V20261028130000: importados podem ficar sem data de nascimento.
            stmt.execute("alter table adra.assistido alter column data_nascimento drop not null");
        }

        coordenador = criarUsuario(NomeNivelPermissao.COORDENADOR, "coord-ft18@teste.local");
        educadorSocial = criarUsuario(NomeNivelPermissao.EDUCADOR_SOCIAL, "educador-ft18@teste.local");
        assistenteSocial = criarUsuario(NomeNivelPermissao.ASSISTENTE_SOCIAL, "assistente-ft18@teste.local");
        oficineiro = criarUsuario(NomeNivelPermissao.OFICINEIRO, "oficineiro-ft18@teste.local");
        autenticarComo(coordenador);

        assistido = assistidoRepository.save(Assistido.builder()
                .nomeCompleto("Ana Avisos")
                .dataNascimento(LocalDate.of(2014, 5, 10))
                .dataEntrada(HOJE.minusMonths(6))
                .status(StatusGeral.ATIVO)
                .build());
    }

    @Test
    @DisplayName("Fluxo do card: a recomendação aparece em Controle disciplinar e some ao gerar a advertência")
    void recomendacaoSomeAoGerarAdvertencia() {
        MotivoDisciplinar palavrao = criarMotivo("Falar palavrão", NivelDisciplinar.OCORRENCIA);
        registrar(palavrao);
        registrar(palavrao);
        Long origemId = registrar(palavrao);

        assertThat(avisosComo(coordenador))
                .extracting(AvisoDTO::tipo, AvisoDTO::assistidoId)
                .contains(tuple(TipoAviso.RECOMENDACAO_ADVERTENCIA, assistido.getAssistidoId()));

        autenticarComo(coordenador);
        disciplinarService.gerarPelaRecomendacao(origemId,
                new GerarPelaRecomendacaoRequestDTO(HOJE, "Advertência pela 3ª ocorrência", null, null));

        assertThat(avisosComo(coordenador))
                .extracting(AvisoDTO::tipo)
                .doesNotContain(TipoAviso.RECOMENDACAO_ADVERTENCIA)
                .contains(TipoAviso.DOCUMENTO_A_ASSINAR);
    }

    @Test
    @DisplayName("CA-FT18.1: suspensão de hoje vai para Coordenador e Educador; recomendação só para o Coordenador")
    void cadaPerfilVeOQueResolve() {
        MotivoDisciplinar bullying = criarMotivo("Bullying", NivelDisciplinar.SUSPENSAO);
        registrar(bullying);

        assertThat(avisosComo(coordenador)).extracting(AvisoDTO::tipo).contains(TipoAviso.SUSPENSO_HOJE);
        assertThat(avisosComo(educadorSocial)).extracting(AvisoDTO::tipo).contains(TipoAviso.SUSPENSO_HOJE)
                .doesNotContain(TipoAviso.DOCUMENTO_A_ASSINAR);
        assertThat(avisosComo(assistenteSocial)).extracting(AvisoDTO::tipo).doesNotContain(TipoAviso.SUSPENSO_HOJE);
        assertThat(avisosComo(oficineiro)).isEmpty();
    }

    @Test
    @DisplayName("Cadastro sem data de nascimento avisa Coordenador e Assistente Social, não o Educador")
    void cadastroSemNascimento() {
        assistidoRepository.save(Assistido.builder()
                .nomeCompleto("Bruno Importado")
                .dataEntrada(HOJE.minusMonths(1))
                .status(StatusGeral.ATIVO)
                .build());

        assertThat(avisosComo(assistenteSocial)).extracting(AvisoDTO::tipo, AvisoDTO::titulo)
                .contains(tuple(TipoAviso.CADASTRO_SEM_NASCIMENTO, "1 atendido(s) sem data de nascimento"));
        assertThat(avisosComo(coordenador)).extracting(AvisoDTO::tipo).contains(TipoAviso.CADASTRO_SEM_NASCIMENTO);
        assertThat(avisosComo(educadorSocial)).extracting(AvisoDTO::tipo).doesNotContain(TipoAviso.CADASTRO_SEM_NASCIMENTO);
    }

    private List<AvisoDTO> avisosComo(Usuario usuario) {
        return avisoService.doPerfil(usuario.getNivelPermissao().getNome());
    }

    private Long registrar(MotivoDisciplinar motivo) {
        autenticarComo(coordenador);
        return disciplinarService.registrar(assistido.getAssistidoId(),
                new RegistroDisciplinarRequestDTO(motivo.getMotivoId(), HOJE, "Descrição do fato", null, null, null))
                .registro().registroId();
    }

    private MotivoDisciplinar criarMotivo(String nome, NivelDisciplinar nivel) {
        return motivoRepository.save(MotivoDisciplinar.builder().nome(nome).nivel(nivel).build());
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
