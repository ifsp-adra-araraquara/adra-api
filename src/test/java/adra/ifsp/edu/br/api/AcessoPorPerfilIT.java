package adra.ifsp.edu.br.api;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.EDUCADOR_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.OFICINEIRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaTurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.domain.service.TokenService;
import adra.ifsp.edu.br.api.infra.SupabaseAdminClient;
import adra.ifsp.edu.br.api.infra.TurnstileClient;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;

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

    @Autowired
    private OficinaRepository oficinaRepository;

    @Autowired
    private TurmaRepository turmaRepository;

    @Autowired
    private OficinaTurmaRepository oficinaTurmaRepository;

    @Autowired
    private TokenService tokenService;

    @MockitoBean
    private SupabaseAdminClient supabaseAdminClient;

    @MockitoBean
    private TurnstileClient turnstile;

    @MockitoBean
    private ArmazenamentoArquivos storage;

    @MockitoBean
    private JavaMailSender mailSender;

    static Stream<Arguments> matriz() {
        return Stream.of(
                arguments(HttpMethod.GET, "/api/assistidos", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/assistidos/contagem", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/arquivos/fotos-perfil?entidadeTipo=ASSISTIDO&entidadeIds=" + INEXISTENTE, Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/arquivos/fotos-perfil?entidadeTipo=PRE_CADASTRO&entidadeIds=" + INEXISTENTE, Set.of()),
                arguments(HttpMethod.GET, "/api/assistidos/" + INEXISTENTE, Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/responsaveis", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.DELETE, "/api/assistidos/" + INEXISTENTE + "/responsaveis/1", Set.of()),

                // aprovar e reprovar têm corpo obrigatório: o 403 deles está no PreCadastroIT.
                arguments(HttpMethod.GET, "/api/pre-cadastro", Set.of(COORDENADOR, ASSISTENTE_SOCIAL)),
                arguments(HttpMethod.GET, "/api/pre-cadastro/" + INEXISTENTE, Set.of(COORDENADOR, ASSISTENTE_SOCIAL)),
                arguments(HttpMethod.GET, "/api/pre-cadastro/" + INEXISTENTE + "/rascunho-assistido", Set.of(COORDENADOR, ASSISTENTE_SOCIAL)),
                arguments(HttpMethod.GET, "/api/pre-cadastro/" + INEXISTENTE + "/arquivos/1/url", Set.of(COORDENADOR, ASSISTENTE_SOCIAL)),

                // gravar a chamada (PUT) tem corpo obrigatório, então fica fora desta matriz.
                arguments(HttpMethod.GET, "/api/chamadas/assistido/" + INEXISTENTE + "/frequencia", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/chamadas/turma/" + INEXISTENTE + "/dia/2026-10-30", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/chamadas/status?inicio=2026-10-01&fim=2026-10-31", Set.of(COORDENADOR)),

                arguments(HttpMethod.GET, "/api/avisos", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL, OFICINEIRO)),

                arguments(HttpMethod.GET, "/api/disciplinar/registros", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.PATCH, "/api/disciplinar/" + INEXISTENTE + "/documento-devolvido", Set.of(COORDENADOR)),

                arguments(HttpMethod.GET, "/api/turmas", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/oficinas", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.PATCH, "/api/oficinas/" + INEXISTENTE + "/inativar", Set.of(COORDENADOR)),

                arguments(HttpMethod.GET, "/api/semanarios/oficina/" + INEXISTENTE, Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL)),
                arguments(HttpMethod.GET, "/api/semanarios/meus", Set.of(OFICINEIRO)),
                arguments(HttpMethod.GET, "/api/excecoes-calendario", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL, OFICINEIRO)),
                arguments(HttpMethod.GET, "/api/notas-calendario", Set.of(COORDENADOR, ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL, OFICINEIRO)),
                arguments(HttpMethod.DELETE, "/api/notas-calendario/" + INEXISTENTE, Set.of(COORDENADOR)),

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

    @ParameterizedTest(name = "/api/usuarios/me com {0}")
    @EnumSource(NomeNivelPermissao.class)
    void cadaPerfilLeEAtualizaOProprioPerfilSemAlterarEmailNemPerfil(NomeNivelPermissao perfil) throws Exception {
        Usuario usuario = criarUsuario(perfil, false);
        Usuario outroUsuario = criarUsuario(COORDENADOR, true);
        String emailOriginal = usuario.getEmail();
        RequestPostProcessor autenticado = comoUsuario(usuario);

        mvc.perform(get("/api/usuarios/me").with(autenticado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuario.getUsuarioId()))
                .andExpect(jsonPath("$.email").value(emailOriginal))
                .andExpect(jsonPath("$.nivelPermissao").value(perfil.name()));

        String payload = """
                {
                  "usuarioId": %d,
                  "nomeCompleto": "Nome Atualizado %s",
                  "telefone": "16991234567",
                  "email": "invasor@teste.local",
                  "nivelPermissao": "COORDENADOR",
                  "administrador": true,
                  "ativo": false
                }
                """.formatted(outroUsuario.getUsuarioId(), perfil.name());

        mvc.perform(put("/api/usuarios/me")
                        .with(autenticado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuario.getUsuarioId()))
                .andExpect(jsonPath("$.nomeCompleto").value("Nome Atualizado " + perfil.name()))
                .andExpect(jsonPath("$.telefone").value("16991234567"))
                .andExpect(jsonPath("$.email").value(emailOriginal))
                .andExpect(jsonPath("$.nivelPermissao").value(perfil.name()))
                .andExpect(jsonPath("$.administrador").value(false))
                .andExpect(jsonPath("$.ativo").value(true));

        Usuario noBanco = usuarioRepository.findById(usuario.getUsuarioId()).orElseThrow();
        assertThat(noBanco.getNomeCompleto()).isEqualTo("Nome Atualizado " + perfil.name());
        assertThat(noBanco.getTelefone()).isEqualTo("16991234567");
        assertThat(noBanco.getEmail()).isEqualTo(emailOriginal);
        assertThat(noBanco.isAdministrador()).isFalse();
        assertThat(noBanco.isAtivo()).isTrue();

        Usuario outroNoBanco = usuarioRepository.findById(outroUsuario.getUsuarioId()).orElseThrow();
        assertThat(outroNoBanco.getNomeCompleto()).isEqualTo("Usuario COORDENADOR");
    }

    @ParameterizedTest(name = "FOTO_PERFIL do próprio usuário ({0})")
    @EnumSource(NomeNivelPermissao.class)
    void cadaPerfilEnviaConsultaERemoveAPropriaFotoDePerfilMasNaoADeOutro(NomeNivelPermissao perfil) throws Exception {
        when(storage.provedor()).thenReturn(ProvedorArmazenamento.SUPABASE);
        when(storage.urlsTemporarias(anyList(), any())).thenAnswer(inv -> {
            List<String> chaves = inv.getArgument(0);
            return Map.of(chaves.getFirst(), URI.create("https://storage.test/" + chaves.getFirst()));
        });

        Usuario eu = criarUsuario(perfil, false);
        Usuario outro = criarUsuario(COORDENADOR, false);
        RequestPostProcessor comoEu = comoUsuario(eu);
        byte[] png = pngMinimo();

        mvc.perform(multipart("/api/arquivos")
                        .file(new MockMultipartFile("arquivo", "foto.png", MediaType.IMAGE_PNG_VALUE, png))
                        .param("entidadeTipo", "USUARIO")
                        .param("entidadeId", outro.getUsuarioId().toString())
                        .param("categoria", "FOTO_PERFIL")
                        .with(comoEu))
                .andExpect(status().isForbidden());

        String salvoJson = mvc.perform(multipart("/api/arquivos")
                        .file(new MockMultipartFile("arquivo", "foto.png", MediaType.IMAGE_PNG_VALUE, png))
                        .param("entidadeTipo", "USUARIO")
                        .param("entidadeId", eu.getUsuarioId().toString())
                        .param("categoria", "FOTO_PERFIL")
                        .with(comoEu))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number arquivoId = JsonPath.read(salvoJson, "$.arquivoId");

        mvc.perform(get("/api/arquivos/fotos-perfil")
                        .param("entidadeTipo", "USUARIO")
                        .param("entidadeIds", eu.getUsuarioId().toString())
                        .with(comoEu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + eu.getUsuarioId() + ".url").exists());

        mvc.perform(delete("/api/arquivos/{id}", arquivoId).with(comoUsuario(outro)))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/arquivos/{id}", arquivoId).with(comoEu))
                .andExpect(status().isNoContent());
    }

    /** Sem login e contra o schema das migrations (enum de auditoria incluso). Dados inventados. */
    @Test
    void linkPublicoDePreCadastroGravaSemLogin() throws Exception {
        when(turnstile.tokenValido("token-valido")).thenReturn(true);
        when(storage.provedor()).thenReturn(ProvedorArmazenamento.SUPABASE);
        String corpo = """
                {"nomeCompleto": "Crianca Teste", "dataNascimento": "2016-04-12", "cpf": "56532228281", "sexo": "FEMININO", "site": "",
                 "dados": {"termos": {"aceiteLgpd": true, "autorizaUsoImagem": false, "cienteFrequencia": true},
                   "mae": {"nomeCompleto": "Mae Teste", "dataNascimento": "1990-01-20", "cpf": "96234168493",
                           "escolaridade": "MEDIO_COMPLETO", "estadoCivil": "SOLTEIRO", "renda": 1500, "telefone": "16991234567"},
                   "enderecoFamilia": {"logradouro": "Rua das Flores"},
                   "transferencia": {"recebe": false, "nis": "12345678901"},
                   "saude": {"possuiProblema": false, "contatoEmergenciaNome": "Tia Teste", "contatoEmergenciaTelefone": "16998765432"},
                   "autorizacoes": {"eventosPasseios": true, "irEmboraSozinho": true}}}
                """;

        mvc.perform(multipart("/api/pre-cadastro")
                        .file(new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE, corpo.getBytes()))
                        .file(new MockMultipartFile("comprovanteEndereco", "comprovante.pdf", MediaType.APPLICATION_PDF_VALUE, pdfMinimo()))
                        .file(new MockMultipartFile("foto", "foto.png", MediaType.IMAGE_PNG_VALUE, pngMinimo()))
                        .file(new MockMultipartFile("documentosCrianca", "rg.pdf", MediaType.APPLICATION_PDF_VALUE, pdfMinimo()))
                        .file(new MockMultipartFile("documentosResponsavel", "rg-mae.pdf", MediaType.APPLICATION_PDF_VALUE, pdfMinimo()))
                        .param("captcha", "token-valido")
                        .header("X-Forwarded-For", "acesso-" + UUID.randomUUID()))
                .andExpect(status().isCreated());
    }

    @Test
    void oficineiroListaSoOsGruposDeleESemBuscarPorCpf() throws Exception {
        Usuario oficineiro = criarUsuario(OFICINEIRO, false);
        Long grupoDele = grupoDaOficinaDe(oficineiro);
        Long outroGrupo = grupoDaOficinaDe(criarUsuario(OFICINEIRO, false));
        RequestPostProcessor comoOficineiro = jwt()
                .jwt(token -> token.subject(oficineiro.getUsuarioId().toString()).claim(TokenService.CLAIM_PERFIL, OFICINEIRO.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_OFICINEIRO"));

        for (String url : List.of("/api/assistidos", "/api/assistidos/contagem")) {
            mvc.perform(get(url).param("turmaId", grupoDele.toString()).with(comoOficineiro)).andExpect(status().isOk());
            int buscaPorNome = mvc.perform(get(url).param("turmaId", grupoDele.toString()).param("busca", "Ana").with(comoOficineiro))
                    .andReturn().getResponse().getStatus();
            assertThat(buscaPorNome).isNotEqualTo(403);
            mvc.perform(get(url).param("turmaId", grupoDele.toString()).param("busca", "123.4").with(comoOficineiro)).andExpect(status().isForbidden());
            mvc.perform(get(url).param("turmaId", outroGrupo.toString()).with(comoOficineiro)).andExpect(status().isForbidden());
            mvc.perform(get(url).with(comoOficineiro)).andExpect(status().isForbidden());
        }
    }

    @Test
    void tamanhoDaListagemDeAssistidosTemLimite() throws Exception {
        mvc.perform(get("/api/assistidos").param("tamanho", "10000").with(comPerfil(COORDENADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanho").value(500));
    }

    @Test
    void tokenDeUsuarioDesativadoEhRecusado() throws Exception {
        Usuario usuario = criarUsuario(COORDENADOR, false);
        String autorizacao = "Bearer " + tokenService.gerarToken(usuario);

        mvc.perform(get("/api/avisos").header("Authorization", autorizacao)).andExpect(status().isOk());

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);

        mvc.perform(get("/api/avisos").header("Authorization", autorizacao)).andExpect(status().isUnauthorized());
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

    @Test
    void coordenadorCriaNotaNoCalendarioETodosPodemVer() throws Exception {
        String corpo = """
                {"dataNota": "2026-11-15", "horario": "19:00", "titulo": "Reunião geral", "descricao": "Auditório principal"}
                """;

        mvc.perform(post("/api/notas-calendario").with(comPerfil(OFICINEIRO)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());

        String criadaJson = mvc.perform(post("/api/notas-calendario").with(comPerfil(COORDENADOR)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number notaId = JsonPath.read(criadaJson, "$.notaId");
        assertThat((String) JsonPath.read(criadaJson, "$.titulo")).isEqualTo("Reunião geral");
        assertThat((String) JsonPath.read(criadaJson, "$.horario")).startsWith("19:00");

        String listaJson = mvc.perform(request(HttpMethod.GET, "/api/notas-calendario").with(comPerfil(OFICINEIRO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> titulos = JsonPath.read(listaJson, "$[*].titulo");
        assertThat(titulos).contains("Reunião geral");

        mvc.perform(request(HttpMethod.DELETE, "/api/notas-calendario/" + notaId).with(comPerfil(COORDENADOR)))
                .andExpect(status().isNoContent());
    }

    private static byte[] pdfMinimo() {
        return "%PDF-1.4\n%%EOF".getBytes();
    }

    private static byte[] pngMinimo() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
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

    private Long grupoDaOficinaDe(Usuario oficineiro) {
        Oficina oficina = new Oficina();
        oficina.setNomeOficina("Oficina " + UUID.randomUUID());
        oficina.setOficineiroResponsavel(oficineiro);
        oficinaRepository.save(oficina);

        Turma grupo = new Turma();
        grupo.setNomeTurma("Grupo " + UUID.randomUUID());
        grupo.setTurno(Turno.MANHA);
        grupo.setCapacidade(20);
        grupo.setDiasDaSemana(new ArrayList<>(List.of(DayOfWeek.MONDAY)));
        turmaRepository.save(grupo);

        OficinaTurma vinculo = new OficinaTurma();
        vinculo.setOficina(oficina);
        vinculo.setTurma(grupo);
        oficinaTurmaRepository.save(vinculo);
        return grupo.getTurmaId();
    }

    private List<String> modulosNoLogin(Usuario usuario) throws Exception {
        String resposta = mvc.perform(post("/api/auth/login").with(jwt().jwt(token -> token.subject(usuario.getAuthUid().toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.usuario.modulos[*].codigo");
    }

    private RequestPostProcessor comoUsuario(Usuario usuario) {
        NomeNivelPermissao perfil = usuario.getNivelPermissao().getNome();
        return jwt()
                .jwt(token -> token
                        .subject(usuario.getUsuarioId().toString())
                        .claim(TokenService.CLAIM_PERFIL, perfil.name())
                        .claim(TokenService.CLAIM_ADMINISTRADOR, usuario.isAdministrador()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
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
