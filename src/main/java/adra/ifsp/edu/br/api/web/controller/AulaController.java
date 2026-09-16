package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.aula.AulaComDetalhesResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.aula.AulaResponseDTO;
import adra.ifsp.edu.br.api.domain.model.CriacaoAulas;
import adra.ifsp.edu.br.api.domain.service.AulaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final AulaService aulaService;

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @PostMapping
    public ResponseEntity<AulaResponseDTO> cadastrar(
            @Valid @RequestBody AulaRequestDTO aulaRequestDTO
    ) {
        return ResponseEntity.ok(aulaService.cadastrar(aulaRequestDTO));
    }


    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @PostMapping("/varias-aulas")
    public ResponseEntity<Boolean> cadastrarVariasAulas(@RequestBody CriacaoAulas criacaoAulas){
        return ResponseEntity.ok(aulaService.cadastrarVariasAulas(criacaoAulas));
    }

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping
    public ResponseEntity<List<AulaResponseDTO>> listar(
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) String nomeTurma,
            @RequestParam(required = false) String titulo
    ) {
        return ResponseEntity.ok(
                aulaService.listarComFiltros(turmaId, nomeTurma, titulo)
        );
    }

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping("/{id}")
    public ResponseEntity<AulaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(aulaService.buscarPorId(id));
    }

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping("/turma/{idTurma}")
    public ResponseEntity<List<AulaResponseDTO>> buscarPorTurma(@PathVariable Long idTurma) {
        return ResponseEntity.ok(aulaService.findByTurma(idTurma));
    }

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping("/turma/{idTurma}/detalhes")
    public ResponseEntity<List<AulaComDetalhesResponseDTO>> buscarPorTurmaComDetalhes(@PathVariable Long idTurma) {
        return ResponseEntity.ok(aulaService.findByTurmaComDetalhes(idTurma));
    }

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<AulaResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AulaRequestDTO aulaRequestDTO
    ) {
        return ResponseEntity.ok(aulaService.atualizar(id, aulaRequestDTO));
    }

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        aulaService.deletar(id);
        return ResponseEntity.noContent().build();
    }


}
