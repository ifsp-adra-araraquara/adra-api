package adra.ifsp.edu.br.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.domain.service.TokenService;
import adra.ifsp.edu.br.api.infra.SupabaseAdminClient;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.hikari.data-source-properties.stringtype=unspecified")
class RedefinicaoSenhaIT {

    private static final String LINK = "http://localhost:4200/redefinir-senha?token_hash=token-teste&type=recovery";

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

    private Usuario usuario;

    @BeforeEach
    void criarUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivelPermissaoRepository.findByNome(NomeNivelPermissao.COORDENADOR).orElseThrow())
                .nomeCompleto("Usuario Redefinicao")
                .email("redefinicao-" + UUID.randomUUID() + "@teste.local")
                .authUid(UUID.randomUUID())
                .build());

        when(supabaseAdminClient.gerarTokenRedefinicao(usuario.getEmail())).thenReturn("token-teste");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    void enviaPorEmailOMesmoLinkQueDevolveAoAdministrador() throws Exception {
        mvc.perform(post("/api/usuarios/{id}/redefinicao-senha", usuario.getUsuarioId()).with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link").value(LINK))
                .andExpect(jsonPath("$.emailEnviado").value(true));

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviado.capture());
        assertThat((String) enviado.getValue().getContent()).contains(LINK);
    }

    @Test
    void devolveOLinkMesmoQuandoOEmailFalha() throws Exception {
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(MimeMessage.class));

        mvc.perform(post("/api/usuarios/{id}/redefinicao-senha", usuario.getUsuarioId()).with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link").value(LINK))
                .andExpect(jsonPath("$.emailEnviado").value(false));
    }

    @Test
    void recusaQuemNaoEAdministrador() throws Exception {
        mvc.perform(post("/api/usuarios/{id}/redefinicao-senha", usuario.getUsuarioId()).with(comPerfil(NomeNivelPermissao.COORDENADOR)))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor comoAdministrador() {
        return jwt()
                .jwt(token -> token
                        .claim(TokenService.CLAIM_PERFIL, NomeNivelPermissao.COORDENADOR.name())
                        .claim(TokenService.CLAIM_ADMINISTRADOR, true))
                .authorities(new SimpleGrantedAuthority("ROLE_COORDENADOR"), new SimpleGrantedAuthority("ADMINISTRADOR"));
    }

    private RequestPostProcessor comPerfil(NomeNivelPermissao perfil) {
        return jwt()
                .jwt(token -> token.claim(TokenService.CLAIM_PERFIL, perfil.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
}
