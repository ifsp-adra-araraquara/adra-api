package adra.ifsp.edu.br.api.web.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * ATENCAO - GAP DE SEGURANCA TEMPORARIO: o criterio do card diz "somente
 * Administrador cadastra", mas isso so' pode ser aplicado de verdade quando
 * o JWT + @PreAuthorize entrarem (decisao consciente de adiar). Ate' la', QUALQUER requisicao sem
 * autenticacao consegue criar um usuario ADMINISTRADOR por este endpoint.
 * Nao expor esta API fora da rede/ambiente de desenvolvimento enquanto
 * isso nao for resolvido.
 *
 * Quando o JWT entrar, a anotacao correta em cadastrar/atualizar/alterarStatus e':
 *   @PreAuthorize("hasRole('ADMINISTRADOR')")
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> cadastrar(@Valid @RequestBody UsuarioRequestDTO dto) {
        UsuarioResponseDTO criado = usuarioService.cadastrar(dto);
        return ResponseEntity.created(URI.create("/api/usuarios/" + criado.usuarioId())).body(criado);
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizar(@PathVariable Long id,
                                                          @Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UsuarioResponseDTO> alterarStatus(@PathVariable Long id,
                                                              @Valid @RequestBody UsuarioStatusRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.alterarStatus(id, dto));
    }

    // Propositalmente sem DELETE: usuario nunca e' removido fisicamente
    // (comentario original do schema). Use PATCH /{id}/status com ativo=false.
}
