package adra.ifsp.edu.br.api.web.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.usuario.DefinirSenhaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.service.UsuarioAdminService;
import adra.ifsp.edu.br.api.domain.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioAdminService usuarioAdminService;

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> cadastrar(@Valid @RequestBody UsuarioRequestDTO dto) {
        UsuarioResponseDTO criado = usuarioAdminService.cadastrar(dto);
        return ResponseEntity.created(URI.create("/api/usuarios/" + criado.usuarioId())).body(criado);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/senha")
    public ResponseEntity<Void> definirSenha(@Valid @RequestBody DefinirSenhaRequestDTO dto) {
        usuarioAdminService.definirSenha(dto);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR')")
    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR')")
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizar(@PathVariable Long id,
                                                          @Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizar(id, dto));
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<UsuarioResponseDTO> alterarStatus(@PathVariable Long id,
                                                              @Valid @RequestBody UsuarioStatusRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.alterarStatus(id, dto));
    }

    // Propositalmente sem DELETE: usuario nunca e' removido fisicamente
    // (comentario original do schema). Use PATCH /{id}/status com ativo=false.
}

