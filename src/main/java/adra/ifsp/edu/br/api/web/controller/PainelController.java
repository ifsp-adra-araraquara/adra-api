package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.painel.PainelHojeDTO;
import adra.ifsp.edu.br.api.domain.dto.painel.PainelMensalDTO;
import adra.ifsp.edu.br.api.domain.service.PainelService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * FT-05 — Painel do Coordenador (CA-FT5.7: só o Coordenador). Dois endpoints
 * para o front pedir em paralelo e, ao trocar de mês, refazer só o mensal.
 */
@RestController
@RequestMapping("/api/painel")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDENADOR')")
public class PainelController {

    private final PainelService painelService;

    /** Números do mês (`mes` = yyyy-MM; sem ele, o mês atual), opcionalmente de uma oficina. */
    @GetMapping("/mensal")
    public ResponseEntity<PainelMensalDTO> mensal(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestParam(required = false) Long oficinaId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(painelService.mensal(mes != null ? mes : YearMonth.now(), oficinaId));
    }

    /** "Precisa de atenção hoje" — independe do mês escolhido. */
    @GetMapping("/hoje")
    public ResponseEntity<PainelHojeDTO> hoje() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(painelService.hoje());
    }
}
