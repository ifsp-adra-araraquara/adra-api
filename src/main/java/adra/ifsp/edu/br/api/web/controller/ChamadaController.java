package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.service.PresencaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// CA-65.5: só Coordenador e Sociopedagógico lançam/editam chamada — OFICINEIRO
// saiu da lista (não é um perfil de usuário do sistema no MVP, ver decisão
// já fechada no projeto: Oficineiro é campo de texto, não login).
@RestController
@RequestMapping("/api/chamadas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
public class ChamadaController {

    private final PresencaService presencaService;

    @PostMapping
    public ResponseEntity<PresencaResponseDTO> registrarPresenca(
            @Valid @RequestBody PresencaRequestDTO presencaRequestDTO
    ) {
        return ResponseEntity.ok(presencaService.registrarPresenca(presencaRequestDTO));
    }

    @PostMapping("/aula/{idAula}")
    public ResponseEntity<List<PresencaResponseDTO>> registrarChamada(
            @PathVariable Long idAula,
            @RequestBody List<PresencaRequestDTO> presencas
    ) {
        return ResponseEntity.ok(presencaService.registrarChamada(idAula, presencas));
    }

    @GetMapping("/aula/{idAula}")
    public ResponseEntity<List<PresencaResponseDTO>> buscarPorAula(@PathVariable Long idAula) {
        return ResponseEntity.ok(presencaService.buscarPorAula(idAula));
    }

    @GetMapping("/assistido/{idAssistido}")
    public ResponseEntity<List<PresencaResponseDTO>> buscarPorAssistido(@PathVariable Long idAssistido) {
        return ResponseEntity.ok(presencaService.buscarPorAssistido(idAssistido));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PresencaResponseDTO> atualizarPresenca(
            @PathVariable Long id,
            @Valid @RequestBody PresencaRequestDTO presencaRequestDTO
    ) {
        return ResponseEntity.ok(presencaService.atualizarPresenca(id, presencaRequestDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPresenca(@PathVariable Long id) {
        presencaService.deletarPresenca(id);
        return ResponseEntity.noContent().build();
    }
}
