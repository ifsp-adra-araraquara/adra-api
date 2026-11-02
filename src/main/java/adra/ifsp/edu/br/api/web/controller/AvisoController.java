package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.aviso.AvisoDTO;
import adra.ifsp.edu.br.api.domain.service.AvisoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** FT-18 — central de avisos: cada perfil recebe só os avisos que resolve (CA-FT18.1). */
@RestController
@RequestMapping("/api/avisos")
@RequiredArgsConstructor
public class AvisoController {

    private final AvisoService avisoService;

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL', 'OFICINEIRO')")
    @GetMapping
    public ResponseEntity<List<AvisoDTO>> listar() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(avisoService.doUsuarioAtual());
    }
}
