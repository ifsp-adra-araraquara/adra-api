package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.usuario.MeuPerfilRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.mapper.UsuarioMapper;
import adra.ifsp.edu.br.api.domain.model.Arquivo;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeuPerfilTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NivelPermissaoRepository nivelPermissaoRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private UsuarioAutenticadoService usuarioAutenticadoService;

    @Mock
    private TurmaRepository turmaRepository;

    @Mock
    private TurmaAlunosRepository turmaAlunosRepository;

    @Mock
    private OficinaRepository oficinaRepository;

    @Mock
    private ArquivoRepository arquivoRepository;

    @InjectMocks
    private AcessoTurmaService acessoTurmaService;

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        UsuarioMapper usuarioMapper = new UsuarioMapper();
        usuarioService = new UsuarioService(
                usuarioRepository,
                nivelPermissaoRepository,
                usuarioMapper,
                auditoriaService,
                usuarioAutenticadoService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(NomeNivelPermissao.class)
    void cadaPerfilLeEAtualizaOProprioPerfilSemAlterarEmailPerfilOuAdministrador(NomeNivelPermissao perfil) {
        NivelPermissao nivel = NivelPermissao.builder()
                .nome(perfil)
                .build();
        Usuario logado = Usuario.builder()
                .usuarioId(77L)
                .nomeCompleto("Nome Original " + perfil)
                .email("original." + perfil.name().toLowerCase() + "@adra.org.br")
                .telefone("19999990000")
                .nivelPermissao(nivel)
                .administrador(perfil == NomeNivelPermissao.COORDENADOR)
                .ativo(true)
                .build();

        when(usuarioAutenticadoService.getUsuarioAtual()).thenReturn(logado);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponseDTO lido = usuarioService.buscarMeuPerfil();
        assertThat(lido.usuarioId()).isEqualTo(77L);
        assertThat(lido.nomeCompleto()).isEqualTo("Nome Original " + perfil);
        assertThat(lido.email()).isEqualTo("original." + perfil.name().toLowerCase() + "@adra.org.br");
        assertThat(lido.nivelPermissao()).isEqualTo(perfil);

        UsuarioResponseDTO atualizado = usuarioService.atualizarMeuPerfil(
                new MeuPerfilRequestDTO("  Novo Nome " + perfil + "  ", "19988887777")
        );

        assertThat(atualizado.usuarioId()).isEqualTo(77L);
        assertThat(atualizado.nomeCompleto()).isEqualTo("Novo Nome " + perfil);
        assertThat(atualizado.telefone()).isEqualTo("19988887777");
        // E-mail, perfil, flag administrador e ativo permanecem intactos
        assertThat(atualizado.email()).isEqualTo("original." + perfil.name().toLowerCase() + "@adra.org.br");
        assertThat(atualizado.nivelPermissao()).isEqualTo(perfil);
        assertThat(atualizado.administrador()).isEqualTo(perfil == NomeNivelPermissao.COORDENADOR);
        assertThat(atualizado.ativo()).isTrue();

        verify(auditoriaService).registrar(
                eq(ModuloSistema.USUARIOS),
                eq("usuario"),
                eq(77L),
                eq(AcaoSistema.EDITAR),
                any(),
                any(),
                any()
        );
    }

    @Test
    void acessoTurmaReconheceApenasOProprioUsuarioESuaPropriaFotoDePerfil() {
        autenticarUsuario(42L, NomeNivelPermissao.EDUCADOR_SOCIAL);

        assertThat(acessoTurmaService.ehProprioUsuario(42L)).isTrue();
        assertThat(acessoTurmaService.ehProprioUsuario(99L)).isFalse();
        assertThat(acessoTurmaService.ehProprioUsuario(List.of(42L))).isTrue();
        assertThat(acessoTurmaService.ehProprioUsuario(List.of(42L, 99L))).isFalse();

        Arquivo minhaFoto = new Arquivo();
        minhaFoto.setArquivoId(100L);
        minhaFoto.setEntidadeTipo(EntidadeArquivo.USUARIO);
        minhaFoto.setEntidadeId(42L);
        minhaFoto.setCategoria(CategoriaArquivo.FOTO_PERFIL);

        Arquivo fotoDeOutro = new Arquivo();
        fotoDeOutro.setArquivoId(101L);
        fotoDeOutro.setEntidadeTipo(EntidadeArquivo.USUARIO);
        fotoDeOutro.setEntidadeId(99L);
        fotoDeOutro.setCategoria(CategoriaArquivo.FOTO_PERFIL);

        when(arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(100L)).thenReturn(Optional.of(minhaFoto));
        when(arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(101L)).thenReturn(Optional.of(fotoDeOutro));

        assertThat(acessoTurmaService.ehFotoPerfilDoProprioUsuario(100L)).isTrue();
        assertThat(acessoTurmaService.ehFotoPerfilDoProprioUsuario(101L)).isFalse();
        assertThat(acessoTurmaService.ehArquivoDeUsuario(100L)).isTrue();
    }

    private void autenticarUsuario(Long id, NomeNivelPermissao perfil) {
        Jwt jwt = Jwt.withTokenValue("token-teste")
                .header("alg", "HS256")
                .subject(String.valueOf(id))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
