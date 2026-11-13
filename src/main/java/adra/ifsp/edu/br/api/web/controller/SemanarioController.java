package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import adra.ifsp.edu.br.api.domain.dto.semanario.SemanarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.semanario.SemanarioResponseDTO;
import adra.ifsp.edu.br.api.domain.service.SemanarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/semanarios")
@RequiredArgsConstructor
public class SemanarioController {

    private final SemanarioService semanarioService;

    /** O oficineiro envia (ou atualiza) o semanário da semana de um grupo da sua oficina. */
    @PreAuthorize("hasRole('OFICINEIRO') and @acessoTurma.ehOficineiroDaOficina(#dto.oficinaId())")
    @PostMapping
    public ResponseEntity<SemanarioResponseDTO> enviar(@Valid @RequestBody SemanarioRequestDTO dto) {
        return ResponseEntity.ok(semanarioService.enviar(dto));
    }

    @PreAuthorize("hasRole('COORDENADOR') or (hasRole('OFICINEIRO') and @acessoTurma.podeAcessarSemanario(#id))")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        semanarioService.remover(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tela Semanários: a coordenação vê os semanários de todas as oficinas, paginados.
     * {@code semana}: qualquer dia da semana (vale a segunda); sem ela, todas as semanas.
     */
    @PreAuthorize("hasRole('COORDENADOR')")
    @GetMapping
    public ResponseEntity<PaginaDTO<SemanarioResponseDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana,
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) Long oficinaId,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return ResponseEntity.ok(semanarioService.listar(semana, turmaId, oficinaId, busca,
                Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));
    }

    @PreAuthorize("hasRole('OFICINEIRO')")
    @GetMapping("/meus")
    public ResponseEntity<List<SemanarioResponseDTO>> meus() {
        return ResponseEntity.ok(semanarioService.listarDoOficineiroAtual());
    }

    @PreAuthorize("hasAnyRole('ASSISTENTE_SOCIAL', 'COORDENADOR')")
    @GetMapping("/oficina/{oficinaId}")
    public ResponseEntity<List<SemanarioResponseDTO>> listarPorOficina(@PathVariable Long oficinaId) {
        return ResponseEntity.ok(semanarioService.listarPorOficina(oficinaId));
    }
}
