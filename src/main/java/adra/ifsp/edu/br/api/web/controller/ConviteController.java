package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.usuario.DefinirSenhaRequestDTO;
import adra.ifsp.edu.br.api.domain.service.InvitacaoService;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/convite")
@RequiredArgsConstructor
public class ConviteController {

    private final InvitacaoService invitacaoService;

    @GetMapping("/{token}")
    public ResponseEntity<?> validarConvite(@PathVariable String token) {
        try {
            invitacaoService.validarConvite(token);
            return ResponseEntity.ok("Convite válido");
        } catch (RegraNegocioException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{token}/definir-senha")
    public ResponseEntity<?> definirSenha(
            @PathVariable String token,
            @RequestBody DefinirSenhaRequestDTO dto) {

        try {
            invitacaoService.consumirConviteEDefinirSenha(
                    token,
                    dto.novaSenha()
            );

            return ResponseEntity.ok("Senha definida com sucesso");

        } catch (RegraNegocioException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}