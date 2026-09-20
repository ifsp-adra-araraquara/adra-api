package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.excecao.ExcecaoCalendarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.excecao.ExcecaoCalendarioResponseDTO;
import adra.ifsp.edu.br.api.domain.service.ExcecaoCalendarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/excecoes-calendario")
@RequiredArgsConstructor
public class ExcecaoCalendarioController {

    private final ExcecaoCalendarioService excecaoCalendarioService;

    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping
    public ResponseEntity<ExcecaoCalendarioResponseDTO> cadastrar(
            @Valid @RequestBody ExcecaoCalendarioRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(excecaoCalendarioService.cadastrar(dto));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO', 'OFICINEIRO')")
    @GetMapping
    public ResponseEntity<List<ExcecaoCalendarioResponseDTO>> listar() {
        return ResponseEntity.ok(excecaoCalendarioService.listarTodas());
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO', 'OFICINEIRO')")
    @GetMapping("/{id}")
    public ResponseEntity<ExcecaoCalendarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(excecaoCalendarioService.buscarPorId(id));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        excecaoCalendarioService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
