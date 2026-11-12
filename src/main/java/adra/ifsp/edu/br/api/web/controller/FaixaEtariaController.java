package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.faixaetaria.FaixaEtariaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.faixaetaria.FaixaEtariaResponseDTO;
import adra.ifsp.edu.br.api.domain.service.FaixaEtariaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faixas-etarias")
@RequiredArgsConstructor
public class FaixaEtariaController {

    private final FaixaEtariaService faixaEtariaService;

    @PreAuthorize("hasAnyRole('EDUCADOR_SOCIAL', 'ASSISTENTE_SOCIAL', 'COORDENADOR')")
    @GetMapping
    public ResponseEntity<List<FaixaEtariaResponseDTO>> listar(
            @RequestParam(defaultValue = "false") boolean incluirInativas
    ) {
        return ResponseEntity.ok(faixaEtariaService.listar(incluirInativas));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping
    public ResponseEntity<FaixaEtariaResponseDTO> criar(@Valid @RequestBody FaixaEtariaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(faixaEtariaService.criar(dto));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PutMapping("/{faixaEtariaId}")
    public ResponseEntity<FaixaEtariaResponseDTO> atualizar(
            @PathVariable Long faixaEtariaId,
            @Valid @RequestBody FaixaEtariaRequestDTO dto
    ) {
        return ResponseEntity.ok(faixaEtariaService.atualizar(faixaEtariaId, dto));
    }
}
