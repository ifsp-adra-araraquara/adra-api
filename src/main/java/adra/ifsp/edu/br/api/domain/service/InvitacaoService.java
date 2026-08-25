package adra.ifsp.edu.br.api.domain.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adra.ifsp.edu.br.api.domain.model.InvitacaoUsuario;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.InvitacaoRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import adra.ifsp.edu.br.api.infra.BrevoEmailService;
import adra.ifsp.edu.br.api.infra.SupabaseAdminClient;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class InvitacaoService {

    private final InvitacaoRepository invitacaoRepository;
    private final BrevoEmailService brevoEmailService;
    private final SupabaseAdminClient supabaseAdminClient;
    private final UsuarioService usuarioService;

    public void emitirConvite(String email, Long usuarioId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime validadeAte = LocalDateTime.now().plusDays(7);

        InvitacaoUsuario convite = InvitacaoUsuario.builder()
                .email(email)
                .token(token)
                .validadeAte(validadeAte)
                .consumido(false)
                .build();

        invitacaoRepository.save(convite);

        brevoEmailService.enviarConvite(email, token);
    }

    public InvitacaoUsuario validarConvite(String token) {
        InvitacaoUsuario convite = invitacaoRepository.findByToken(token)
                .orElseThrow(() -> new RegraNegocioException("Convite inválido"));

        if (convite.isConsumido()) {
            throw new RegraNegocioException("Convite já utilizado");
        }

        if (LocalDateTime.now().isAfter(convite.getValidadeAte())) {
            throw new RegraNegocioException("Convite expirado");
        }

        return convite;
    }

    public void consumirConviteEDefinirSenha(String token, String novaSenha) {
        InvitacaoUsuario convite = validarConvite(token);

        Usuario usuario = usuarioService.buscarPorEmail(convite.getEmail());

        supabaseAdminClient.definirSenha(usuario.getAuthUid(), novaSenha);

        convite.setConsumido(true);
        convite.setConsumidoEm(LocalDateTime.now());

        invitacaoRepository.save(convite);
    }
}