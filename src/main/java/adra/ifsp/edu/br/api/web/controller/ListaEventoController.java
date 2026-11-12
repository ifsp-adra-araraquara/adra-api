package adra.ifsp.edu.br.api.web.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.lista.AtendidoListaEventoDTO;
import adra.ifsp.edu.br.api.domain.service.ListaEventoService;
import lombok.RequiredArgsConstructor;

/** Lista de evento/passeio para imprimir. Só o Coordenador. */
@RestController
@RequestMapping("/api/listas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDENADOR')")
public class ListaEventoController {

    private final ListaEventoService service;

    /** Ex.: GET /api/listas/evento?turmaIds=1,2,3&comResponsavel=false */
    @GetMapping("/evento")
    public ResponseEntity<List<AtendidoListaEventoDTO>> evento(
            @RequestParam List<Long> turmaIds,
            @RequestParam(defaultValue = "false") boolean comResponsavel) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.atendidos(turmaIds, comResponsavel));
    }
}
