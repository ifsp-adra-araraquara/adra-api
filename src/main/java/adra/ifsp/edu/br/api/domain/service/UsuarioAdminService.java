package adra.ifsp.edu.br.api.domain.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import adra.ifsp.edu.br.api.domain.dto.usuario.RedefinicaoSenhaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import adra.ifsp.edu.br.api.infra.EmailService;
import adra.ifsp.edu.br.api.infra.SupabaseAdminClient;
import lombok.RequiredArgsConstructor;

/**
 * Fora da transacao e em classe separada de proposito: so' assim enxerga falha
 * de commit e desfaz a identidade ja' criada no Supabase.
 */
@Service
@RequiredArgsConstructor
public class UsuarioAdminService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioAdminService.class);

    private final SupabaseAdminClient supabaseAdminClient;
    private final UsuarioService usuarioService;
    private final EmailService emailService;

    public UsuarioResponseDTO cadastrar(UsuarioRequestDTO dto) {
        exigirAdministrador();
        usuarioService.validarNovoCadastro(dto);

        UUID authUid = supabaseAdminClient.convidar(dto.email());

        return cadastrarUsuarioOuDesfazerIdentidade(dto, authUid);
    }

    /**
     * Cria o usuario no banco. Se falhar, desfaz a identidade criada no
     * Supabase Auth (nao pode sobrar identidade orfa sem usuario correspondente).
     */
    private UsuarioResponseDTO cadastrarUsuarioOuDesfazerIdentidade(UsuarioRequestDTO dto, UUID authUid) {
        try {
            return usuarioService.cadastrar(dto, authUid);
        } catch (RuntimeException e) {
            desfazerIdentidade(authUid);
            throw e;
        }
    }

    public void reenviarConvite(Long id) {
        exigirAdministrador();
        Usuario usuario = buscarComIdentidade(id);

        try {
            supabaseAdminClient.convidar(usuario.getEmail());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                throw new RegraNegocioException("Este usuario ja confirmou o convite. Use Redefinir senha.");
            }
            throw e;
        }
    }

    /** O link volta mesmo se o e-mail falhar: o admin pode repassa-lo por outro canal. */
    public RedefinicaoSenhaResponseDTO redefinirSenha(Long id) {
        exigirAdministrador();
        Usuario usuario = buscarComIdentidade(id);

        String tokenHash = supabaseAdminClient.gerarTokenRedefinicao(usuario.getEmail());

        boolean emailEnviado = true;
        try {
            emailService.enviarRedefinicaoSenha(usuario.getEmail(), tokenHash);
        } catch (RuntimeException e) {
            emailEnviado = false;
            log.error("Falha ao enviar o e-mail de redefinicao de senha do usuario id={}", id, e);
        }

        usuarioService.registrarRedefinicaoSenha(usuario);
        return new RedefinicaoSenhaResponseDTO(emailService.linkRedefinicaoSenha(tokenHash), emailEnviado);
    }

    private Usuario buscarComIdentidade(Long id) {
        Usuario usuario = usuarioService.buscarEntidadePorId(id);
        if (usuario.getAuthUid() == null) {
            throw new RegraNegocioException(
                    "Usuario sem identidade no provedor de autenticacao. Cadastre-o novamente.");
        }
        return usuario;
    }

    public UsuarioResponseDTO alterarStatus(Long id, UsuarioStatusRequestDTO dto) {
        exigirAdministrador();
        validarNaoInativarProprioUsuario(id, dto.ativo());

        Usuario usuario = usuarioService.buscarEntidadePorId(id);
        UsuarioResponseDTO resposta = usuarioService.alterarStatus(id, dto);

        if (usuario.getAuthUid() != null) {
            supabaseAdminClient.alterarStatus(usuario.getAuthUid(), dto.ativo());
        } else {
            log.warn("Usuario id={} email={} nao possui auth_uid associado no Supabase Auth.", id, usuario.getEmail());
            throw new RegraNegocioException(
                    "Usuario nao possui conta no provedor de autenticacao (Supabase Auth). Cadastre um novo usuario para testar a sincronizacao.");
        }

        return resposta;
    }

    private void validarNaoInativarProprioUsuario(Long id, Boolean ativo) {
        if (Boolean.FALSE.equals(ativo)) {
            Long usuarioAutenticadoId = obterIdUsuarioAutenticado();
            if (id.equals(usuarioAutenticadoId)) {
                throw new RegraNegocioException("Não é permitido inativar seu próprio usuário.");
            }
        }
    }

    private Long obterIdUsuarioAutenticado() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return null;
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            try {
                return Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException e) {
                log.warn("Subject do token JWT não é um ID numérico válido: {}", jwt.getSubject());
            }
        }
        return null;
    }

    private void desfazerIdentidade(UUID authUid) {
        try {
            supabaseAdminClient.removerIdentidade(authUid);
        } catch (RuntimeException falhaAoDesfazer) {
            // Engolir para nao mascarar o erro original.
            log.error("Falha ao remover a identidade {} apos erro no cadastro", authUid, falhaAoDesfazer);
        }
    }

    private void exigirAdministrador() {
        // Temporario: vira @PreAuthorize com a US-08.
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String perfil = principal instanceof Jwt jwt ? jwt.getClaimAsString(TokenService.CLAIM_PERFIL) : null;

        if (!NomeNivelPermissao.ADMINISTRADOR.name().equals(perfil)) {
            throw new AcessoNegadoException("Apenas o Administrador pode gerenciar usuarios.");
        }
    }
}