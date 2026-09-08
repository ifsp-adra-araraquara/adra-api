package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarResponseDTO;
import adra.ifsp.edu.br.api.domain.service.VinculoFamiliarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/assistidos/{assistidoId}/responsaveis")
@RequiredArgsConstructor
public class VinculoFamiliarController {

    private final VinculoFamiliarService vinculoFamiliarService;

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR')")
    @PostMapping
    public ResponseEntity<VinculoFamiliarResponseDTO> vincular(@PathVariable Long assistidoId,
                                                                 @Valid @RequestBody VinculoFamiliarRequestDTO dto) {
        VinculoFamiliarResponseDTO criado = vinculoFamiliarService.vincular(assistidoId, dto);
        URI local = URI.create("/api/assistidos/" + assistidoId + "/responsaveis/" + dto.responsavelId());
        return ResponseEntity.created(local).body(criado);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR')")
    @PostMapping("/cadastrar-vincular")
    public ResponseEntity<VinculoFamiliarResponseDTO> cadastrarVincular(
            @PathVariable Long assistidoId,
            @Valid @RequestBody VinculoFamiliarComResponsavelRequestDTO dto
    ) {
        VinculoFamiliarResponseDTO criado = vinculoFamiliarService.cadastrarResponsavelEVincular(assistidoId, dto);
        URI local = URI.create("/api/assistidos/" + assistidoId + "/responsaveis/" + criado.responsavelId());
        return ResponseEntity.created(local).body(criado);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping
    public ResponseEntity<List<VinculoFamiliarResponseDTO>> listar(@PathVariable Long assistidoId) {
        return ResponseEntity.ok(vinculoFamiliarService.listarPorAssistido(assistidoId));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR')")
    @PutMapping("/{responsavelId}")
    public ResponseEntity<VinculoFamiliarResponseDTO> atualizar(@PathVariable Long assistidoId,
                                                                  @PathVariable Long responsavelId,
                                                                  @Valid @RequestBody VinculoFamiliarRequestDTO dto) {
        return ResponseEntity.ok(vinculoFamiliarService.atualizar(assistidoId, responsavelId, dto));
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/{responsavelId}")
    public ResponseEntity<Void> desvincular(@PathVariable Long assistidoId, @PathVariable Long responsavelId) {
        vinculoFamiliarService.desvincular(assistidoId, responsavelId);
        return ResponseEntity.noContent().build();
    }
}
