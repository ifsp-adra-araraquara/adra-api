package adra.ifsp.edu.br.api;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.EDUCADOR_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.OFICINEIRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.domain.service.TokenService;
import adra.ifsp.edu.br.api.infra.SupabaseAdminClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.hikari.data-source-properties.stringtype=unspecified")
class AcessoPorPerfilIT {

    private static final String INEXISTENTE = "999999";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NivelPermissaoRepository nivelPermissaoRepository;

    @MockitoBean
    private SupabaseAdminClient supabaseAdminClient;

    @MockitoBean
    private JavaMailSender mailSender;

    static Stream<Arguments> matriz() {
        return Stream.of(
                arguments(HttpMethod.GET, "/api/assistidos", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL, OFICINEIRO)),
                arguments(HttpMethod.GET, "/api/assistidos/" + INEXISTENTE, Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/assistidos/elegibilidade", Set.of(COORDENADOR, ASSISTENTE_SOCIAL)),
                arguments(HttpMethod.GET, "/api/responsaveis", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.DELETE, "/api/assistidos/" + INEXISTENTE + "/responsaveis/1", Set.of()),

                arguments(HttpMethod.GET, "/api/pre-cadastro/pendentes", Set.of(COORDENADOR, ASSISTENTE_SOCIAL)),
                arguments(HttpMethod.PATCH, "/api/pre-cadastro/" + INEXISTENTE + "/converter", Set.of(COORDENADOR, ASSISTENTE_SOCIAL)),

                arguments(HttpMethod.GET, "/api/chamadas/assistido/" + INEXISTENTE, Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.DELETE, "/api/chamadas/" + INEXISTENTE, Set.of(COORDENADOR, EDUCADOR_SOCIAL)),

                arguments(HttpMethod.GET, "/api/disciplinar/registros", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.PATCH, "/api/disciplinar/" + INEXISTENTE + "/documento-devolvido", Set.of(COORDENADOR)),

                arguments(HttpMethod.GET, "/api/turmas", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/oficinas", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.PATCH, "/api/oficinas/" + INEXISTENTE + "/inativar", Set.of(COORDENADOR)),

                arguments(HttpMethod.GET, "/api/aulas", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL, OFICINEIRO)),
                arguments(HttpMethod.GET, "/api/excecoes-calendario", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL, OFICINEIRO)),
                arguments(HttpMethod.DELETE, "/api/aulas/" + INEXISTENTE, Set.of(COORDENADOR)),

                arguments(HttpMethod.GET, "/api/painel/hoje", Set.of(COORDENADOR)),
                arguments(HttpMethod.GET, "/api/usuarios", Set.of()),
                arguments(HttpMethod.GET, "/api/usuarios/oficineiros", Set.of(COORDENADOR))
        );
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matriz")
    void cadaPerfilAcessaSoOQueAMatrizPermite(HttpMethod metodo, String url, Set<NomeNivelPermissao> permitidos) throws Exception {
        for (NomeNivelPermissao perfil : NomeNivelPermissao.values()) {
            int status = mvc.perform(request(metodo, url).with(comPerfil(perfil))).andReturn().getResponse().getStatus();

            if (permitidos.contains(perfil)) {
                assertThat(status).as("%s em %s %s", perfil, metodo, url).isNotEqualTo(403);
            } else {
                assertThat(status).as("%s em %s %s", perfil, metodo, url).isEqualTo(403);
            }
        }
    }

    @Test
    void soOCoordenadorComAFlagConvida() throws Exception {
        when(supabaseAdminClient.convidar(anyString())).thenReturn(UUID.randomUUID());
        String corpo = """
                {"nomeCompleto": "Oficineiro Convidado", "email": "convite-%s@teste.local", "nivelPermissao": "OFICINEIRO", "administrador": false}
                """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/usuarios").with(comPerfil(COORDENADOR)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(comoAdministrador()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
    }

    @Test
    void assistenteSocialEntraComOsModulosDela() throws Exception {
        assertThat(modulosNoLogin(criarUsuario(ASSISTENTE_SOCIAL, false))).containsExactlyInAnyOrder(
                "DASHBOARD", "ASSISTIDOS", "RESPONSAVEIS", "PRE_CADASTRO", "CHAMADA", "DISCIPLINAR", "OFICINAS", "AULAS");
        assertThat(modulosNoLogin(criarUsuario(EDUCADOR_SOCIAL, false))).doesNotContain("PRE_CADASTRO");
    }

    @Test
    void administradorEntraComOModuloUsuarios() throws Exception {
        assertThat(modulosNoLogin(criarUsuario(COORDENADOR, true))).contains("USUARIOS");
        assertThat(modulosNoLogin(criarUsuario(COORDENADOR, false))).doesNotContain("USUARIOS");
    }

    private Usuario criarUsuario(NomeNivelPermissao perfil, boolean administrador) {
        return usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivelPermissaoRepository.findByNome(perfil).orElseThrow())
                .nomeCompleto("Usuario " + perfil)
                .email("perfil-" + UUID.randomUUID() + "@teste.local")
                .authUid(UUID.randomUUID())
                .administrador(administrador)
                .build());
    }

    private List<String> modulosNoLogin(Usuario usuario) throws Exception {
        String resposta = mvc.perform(post("/api/auth/login").with(jwt().jwt(token -> token.subject(usuario.getAuthUid().toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.usuario.modulos[*].codigo");
    }

    private RequestPostProcessor comPerfil(NomeNivelPermissao perfil) {
        return jwt()
                .jwt(token -> token.subject("1").claim(TokenService.CLAIM_PERFIL, perfil.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    private RequestPostProcessor comoAdministrador() {
        return jwt()
                .jwt(token -> token.subject("1")
                        .claim(TokenService.CLAIM_PERFIL, COORDENADOR.name())
                        .claim(TokenService.CLAIM_ADMINISTRADOR, true))
                .authorities(new SimpleGrantedAuthority("ROLE_COORDENADOR"), new SimpleGrantedAuthority("ADMINISTRADOR"));
    }
}
