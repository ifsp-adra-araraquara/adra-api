package adra.ifsp.edu.br.api.web.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.service.AssistidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assistidos")
@RequiredArgsConstructor
public class AssistidoController {

    private final AssistidoService assistidoService;

    @PostMapping
    public ResponseEntity<AssistidoResponseDTO> cadastrar(@Valid @RequestBody AssistidoRequestDTO dto) {
        AssistidoResponseDTO criado = assistidoService.cadastrar(dto);
        return ResponseEntity.created(URI.create("/api/assistidos/" + criado.assistidoId())).body(criado);
    }

    @GetMapping
    public ResponseEntity<List<AssistidoResponseDTO>> listar() {
        return ResponseEntity.ok(assistidoService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssistidoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(assistidoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssistidoResponseDTO> atualizar(@PathVariable Long id,
                                                            @Valid @RequestBody AssistidoRequestDTO dto) {
        return ResponseEntity.ok(assistidoService.atualizar(id, dto));
    }

    /**
     * Fluxo de encerramento/reativacao - separado do PUT cadastral de
     * proposito (ver comentario no AssistidoStatusRequestDTO).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AssistidoResponseDTO> alterarStatus(@PathVariable Long id,
                                                                @Valid @RequestBody AssistidoStatusRequestDTO dto) {
        return ResponseEntity.ok(assistidoService.alterarStatus(id, dto));
    }
}
