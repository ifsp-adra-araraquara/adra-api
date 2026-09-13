package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResponseDTO;
import adra.ifsp.edu.br.api.domain.service.OficinaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/oficinas")
@RequiredArgsConstructor
public class OficinaController {

    private final OficinaService oficinaService;

    /**
     * Listagem visivel para Coordenador e Sociopedagogico, assim como em
     * Turmas. A restricao de CA-13.3 e' apenas sobre o cadastro.
     */
    @PreAuthorize("hasAnyRole('SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping
    public ResponseEntity<List<OficinaResponseDTO>> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo
    ) {
        return ResponseEntity.ok(oficinaService.listarComFiltros(nome, ativo));
    }

    @PreAuthorize("hasAnyRole('SOCIOPEDAGOGICO', 'COORDENADOR')")
    @GetMapping("/{id}")
    public ResponseEntity<OficinaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(oficinaService.buscarPorId(id));
    }

    /**
     * CA-13.3: apenas Coordenador pode cadastrar. Sociopedagogico (ou
     * qualquer outro perfil) recebe 403, tratado globalmente pelo
     * GlobalExceptionHandler via AuthorizationDeniedException.
     */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping
    public ResponseEntity<OficinaResponseDTO> cadastrar(
            @Valid @RequestBody OficinaRequestDTO oficinaRequestDTO
    ) {
        OficinaResponseDTO oficinaCriada = oficinaService.cadastrar(oficinaRequestDTO);
        return ResponseEntity.ok(oficinaCriada);
    }

    /**
     * Endpoint leve para o alerta NAO BLOQUEANTE de duplicidade por nome
     * no formulario do front (US-13, escopo [FE]). Retorna apenas um
     * booleano; o cadastro continua liberado independentemente do
     * resultado.
     */
    @PreAuthorize("hasRole('COORDENADOR')")
    @GetMapping("/verificar-duplicidade")
    public ResponseEntity<Map<String, Boolean>> verificarDuplicidade(
            @RequestParam String nome
    ) {
        return ResponseEntity.ok(Map.of("possivelDuplicidade", oficinaService.existeComMesmoNome(nome)));
    }

    /**
     * CA-14.2: Editar uma oficina existente.
     * CA-14.4: Apenas Coordenador pode editar (recebe 403 se não for).
     * 
     * PUT /api/oficinas/{id}
     * Body: { "nomeOficina": "...", "oficineiroResponsavel": "..." }
     */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PutMapping("/{id}")
    public ResponseEntity<OficinaResponseDTO> editar(
            @PathVariable Long id,
            @Valid @RequestBody OficinaRequestDTO dto
    ) {
        OficinaResponseDTO atualizada = oficinaService.editar(id, dto);
        return ResponseEntity.ok(atualizada);
    }

    /**
     * CA-14.3: Inativar uma oficina (soft delete, permanece no BD).
     * CA-14.4: Apenas Coordenador pode inativar (recebe 403 se não for).
     * 
     * PATCH /api/oficinas/{id}/inativar
     * Body: vazio
     */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/{id}/inativar")
    public ResponseEntity<OficinaResponseDTO> inativar(@PathVariable Long id) {
        OficinaResponseDTO inativada = oficinaService.inativar(id);
        return ResponseEntity.ok(inativada);
    }

    /**
     * CA-14.3: Reativar uma oficina (inverso de inativar).
     * CA-14.4: Apenas Coordenador pode reativar (recebe 403 se não for).
     * 
     * PATCH /api/oficinas/{id}/reativar
     * Body: vazio
     */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/{id}/reativar")
    public ResponseEntity<OficinaResponseDTO> reativar(@PathVariable Long id) {
        OficinaResponseDTO reativada = oficinaService.reativar(id);
        return ResponseEntity.ok(reativada);
    }
}