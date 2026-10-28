package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.precadastro.DescartarPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResponseDTO;
import adra.ifsp.edu.br.api.domain.service.PreCadastroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FT: link público de pré-cadastro. POST é aberto (sem login — ver
 * SecurityConfig); o resto só Coordenação/Sociopedagógico.
 */
@RestController
@RequestMapping("/api/pre-cadastro")
@RequiredArgsConstructor
public class PreCadastroController {

    private final PreCadastroService service;

    @PostMapping
    public ResponseEntity<Void> registrarPublico(@Valid @RequestBody PreCadastroRequestDTO dto, HttpServletRequest request) {
        service.registrarPublico(dto, ipDoCliente(request));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping("/pendentes")
    public ResponseEntity<List<PreCadastroResponseDTO>> listarPendentes() {
        return ResponseEntity.ok(service.listarPendentes());
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PatchMapping("/{preCadastroId}/descartar")
    public ResponseEntity<PreCadastroResponseDTO> descartar(
            @PathVariable Long preCadastroId,
            @RequestBody(required = false) DescartarPreCadastroRequestDTO dto
    ) {
        return ResponseEntity.ok(service.descartar(preCadastroId, dto));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PatchMapping("/{preCadastroId}/converter")
    public ResponseEntity<PreCadastroResponseDTO> marcarConvertido(@PathVariable Long preCadastroId) {
        return ResponseEntity.ok(service.marcarConvertido(preCadastroId));
    }

    private String ipDoCliente(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
