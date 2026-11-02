package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.nota.NotaCalendarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.nota.NotaCalendarioResponseDTO;
import adra.ifsp.edu.br.api.domain.service.NotaCalendarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/notas-calendario")
@RequiredArgsConstructor
public class NotaCalendarioController {

    private final NotaCalendarioService notaCalendarioService;

    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping
    public ResponseEntity<NotaCalendarioResponseDTO> cadastrar(
            @Valid @RequestBody NotaCalendarioRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notaCalendarioService.cadastrar(dto));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<NotaCalendarioResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody NotaCalendarioRequestDTO dto
    ) {
        return ResponseEntity.ok(notaCalendarioService.atualizar(id, dto));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping
    public ResponseEntity<List<NotaCalendarioResponseDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        return ResponseEntity.ok(notaCalendarioService.listar(dataInicio, dataFim));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping("/{id}")
    public ResponseEntity<NotaCalendarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(notaCalendarioService.buscarPorId(id));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        notaCalendarioService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
