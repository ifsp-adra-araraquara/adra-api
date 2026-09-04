package adra.ifsp.edu.br.api.domain.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import adra.ifsp.edu.br.api.domain.dto.usuario.DefinirSenhaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
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
    private final InvitacaoService invitacaoService; 

    public UsuarioResponseDTO cadastrar(UsuarioRequestDTO dto) {
        exigirAdministrador();
        usuarioService.validarNovoCadastro(dto);


        UUID authUid = supabaseAdminClient.criarIdentidade(dto.email());

        try {
            UsuarioResponseDTO criado = usuarioService.cadastrar(dto, authUid);
            invitacaoService.emitirConvite(dto.email(), criado.usuarioId());
            return criado;
        } catch (RuntimeException e) {
            desfazerIdentidade(authUid);
            throw e;
        }
    }

    /** Provisorio: com a US-02 vira excecao; com a US-05, recebe id no lugar do e-mail. */
    public void definirSenha(DefinirSenhaRequestDTO dto) {
        exigirAdministrador();

        Usuario usuario = usuarioService.buscarPorEmail(dto.email());
        if (usuario.getAuthUid() == null) {
            throw new RegraNegocioException(
                    "Usuario sem identidade no provedor de autenticacao. Cadastre-o novamente.");
        }

        supabaseAdminClient.definirSenha(usuario.getAuthUid(), dto.novaSenha());
        usuarioService.registrarSenhaDefinida(usuario);
    }

    public UsuarioResponseDTO alterarStatus(Long id, UsuarioStatusRequestDTO dto) {
        exigirAdministrador();

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
