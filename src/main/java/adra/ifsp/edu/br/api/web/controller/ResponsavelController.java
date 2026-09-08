package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelResponseDTO;
import adra.ifsp.edu.br.api.domain.service.ResponsavelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/responsaveis")
@RequiredArgsConstructor
public class ResponsavelController {

    private final ResponsavelService responsavelService;

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR')")
    @PostMapping
    public ResponseEntity<ResponsavelResponseDTO> cadastrar(@Valid @RequestBody ResponsavelRequestDTO dto) {
        ResponsavelResponseDTO criado = responsavelService.cadastrar(dto);
        return ResponseEntity.created(URI.create("/api/responsaveis/" + criado.responsavelId())).body(criado);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping
    public ResponseEntity<List<ResponsavelResponseDTO>> listar() {
        return ResponseEntity.ok(responsavelService.listar());
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping("/{id}")
    public ResponseEntity<ResponsavelResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(responsavelService.buscarPorId(id));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<ResponsavelResponseDTO> atualizar(@PathVariable Long id,
                                                              @Valid @RequestBody ResponsavelRequestDTO dto) {
        return ResponseEntity.ok(responsavelService.atualizar(id, dto));
    }
}