
package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.turma.TurmaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.service.TurmaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/turmas")
@RequiredArgsConstructor
public class TurmaController {

    private final TurmaService turmaService;

    @PreAuthorize("hasAnyRole('SOCIOPEDAGO', 'COORDENADOR')")
    @GetMapping
    public ResponseEntity<List<TurmaResponseDTO>> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String turno,
            @RequestParam(required = false) Boolean ativo
    ) {
        Turno turnoEnum = converterTurno(turno);
        return ResponseEntity.ok(
                turmaService.listarComFiltros(nome, turnoEnum, ativo)
        );
    }

    /**
     * Método simples para converter String para Turno
     * Aceita: "MANHA", "Manhã", "manha", "MANHÃ"
     */
    private Turno converterTurno(String turno) {
        if (turno == null || turno.isBlank()) {
            return null;
        }

        String normalized = turno.trim();

        // Lista de possíveis valores para MANHA
        if (normalized.equalsIgnoreCase("MANHA") ||
                normalized.equalsIgnoreCase("MANHÃ") ||
                normalized.equalsIgnoreCase("Manhã")) {
            return Turno.MANHA;
        }

        // Lista de possíveis valores para TARDE
        if (normalized.equalsIgnoreCase("TARDE")) {
            return Turno.TARDE;
        }

        // Lista de possíveis valores para INTEGRAL
        if (normalized.equalsIgnoreCase("INTEGRAL")) {
            return Turno.INTEGRAL;
        }

        // Se não encontrar, retorna null
        return null;
    }

    @PreAuthorize("hasAnyRole('SOCIOPEDAGO', 'COORDENADOR')")
    @PostMapping
    public ResponseEntity<TurmaResponseDTO> cadastrar(
            @Valid @RequestBody TurmaRequestDTO turmaRequestDTO
    ) {
        TurmaResponseDTO turmaCriada = turmaService.cadastrar(turmaRequestDTO);
        return ResponseEntity.ok(turmaCriada);
    }

    @PreAuthorize("hasAnyRole('SOCIOPEDAGO', 'COORDENADOR')")
    @GetMapping("/{id}")
    public ResponseEntity<TurmaResponseDTO> buscarPorId(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(turmaService.buscarPorId(id));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<TurmaResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TurmaRequestDTO turmaRequestDTO
    ) {
        return ResponseEntity.ok(turmaService.atualizar(id, turmaRequestDTO));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<TurmaResponseDTO> alterarStatus(
            @PathVariable Long id,
            @Valid @RequestBody TurmaStatusRequestDTO statusRequestDTO
    ) {
        return ResponseEntity.ok(turmaService.alterarStatus(id, statusRequestDTO));
    }
}