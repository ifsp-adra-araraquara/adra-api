package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.aula.*;
import adra.ifsp.edu.br.api.domain.model.CriacaoAulas;
import adra.ifsp.edu.br.api.domain.service.AulaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final AulaService aulaService;

    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping
    public ResponseEntity<AulaResponseDTO> cadastrar(
            @Valid @RequestBody AulaRequestDTO aulaRequestDTO
    ) {
        return ResponseEntity.ok(aulaService.cadastrar(aulaRequestDTO));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping("/gerar")
    public ResponseEntity<GerarAulasResponseDTO> gerarAulas(
            @Valid @RequestBody CriacaoAulas criacaoAulas
    ) {
        return ResponseEntity.ok(aulaService.gerarAulas(criacaoAulas));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping("/varias-aulas")
    public ResponseEntity<Boolean> cadastrarVariasAulas(@RequestBody CriacaoAulas criacaoAulas) {
        return ResponseEntity.ok(aulaService.cadastrarVariasAulas(criacaoAulas));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<AulaResponseDTO> atualizarStatus(
            @PathVariable Long id,
            @Valid @RequestBody AulaStatusPatchRequestDTO dto
    ) {
        return ResponseEntity.ok(aulaService.atualizarStatus(id, dto));
    }

    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping
    public ResponseEntity<List<AulaResponseDTO>> listar(
            @RequestParam(name = "turmaId", required = false) Long turmaId,
            @RequestParam(name = "turma_id", required = false) Long turmaIdSnake,
            @RequestParam(name = "oficinaId", required = false) Long oficinaId,
            @RequestParam(name = "oficina_id", required = false) Long oficinaIdSnake,
            @RequestParam(name = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(name = "data_inicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicioSnake,
            @RequestParam(name = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(name = "data_fim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFimSnake,
            @RequestParam(required = false) String nomeTurma,
            @RequestParam(required = false) String titulo
    ) {
        Long resolvedTurmaId = turmaId != null ? turmaId : turmaIdSnake;
        Long resolvedOficinaId = oficinaId != null ? oficinaId : oficinaIdSnake;
        LocalDate resolvedDataInicio = dataInicio != null ? dataInicio : dataInicioSnake;
        LocalDate resolvedDataFim = dataFim != null ? dataFim : dataFimSnake;

        return ResponseEntity.ok(
                aulaService.listarComFiltros(resolvedTurmaId, resolvedOficinaId, resolvedDataInicio, resolvedDataFim, nomeTurma, titulo)
        );
    }

    /**
     * Versao enriquecida de listar() (nome da turma, oficineiro responsavel e
     * quantidade de alunos) com filtro por data — usada na tela "Aulas" do
     * sociopedagogico/coordenador: por padrao traz as aulas de hoje, com um
     * filtro de data em cima pra trocar o dia. Nao restringe por turma nem
     * exige turmaId de antemao.
     */
    @PreAuthorize("hasAnyRole('OFICINEIRO', 'SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping("/com-detalhes")
    public ResponseEntity<List<AulaComDetalhesResponseDTO>> listarComDetalhes(
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) String nomeTurma,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAula
    ) {
        return ResponseEntity.ok(
                aulaService.listarComDetalhesComFiltros(turmaId, nomeTurma, titulo, dataAula)
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

    @PreAuthorize("hasRole('COORDENADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<AulaResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AulaRequestDTO aulaRequestDTO
    ) {
        return ResponseEntity.ok(aulaService.atualizar(id, aulaRequestDTO));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        aulaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
