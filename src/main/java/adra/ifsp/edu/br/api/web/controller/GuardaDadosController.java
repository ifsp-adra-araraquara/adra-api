package adra.ifsp.edu.br.api.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.guarda.DescarteDTO;
import adra.ifsp.edu.br.api.domain.dto.guarda.OrfaosDTO;
import adra.ifsp.edu.br.api.domain.dto.guarda.PreviaDescarteDTO;
import adra.ifsp.edu.br.api.domain.service.ArquivosOrfaosService;
import adra.ifsp.edu.br.api.domain.service.DescarteService;
import lombok.RequiredArgsConstructor;

/** Prazo de guarda dos dados (5 anos por matrícula) e limpeza do Storage. Só o Coordenador. */
@RestController
@RequestMapping("/api/guarda-dados")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDENADOR')")
public class GuardaDadosController {

    private final DescarteService descarteService;
    private final ArquivosOrfaosService orfaosService;

    @GetMapping("/previa")
    public ResponseEntity<PreviaDescarteDTO> previa() {
        return ResponseEntity.ok(descarteService.previa());
    }

    @PostMapping("/descartar")
    public ResponseEntity<DescarteDTO> descartar() {
        return ResponseEntity.ok(descarteService.descartar());
    }

    @GetMapping("/descartes")
    public ResponseEntity<List<DescarteDTO>> descartes() {
        return ResponseEntity.ok(descarteService.historico());
    }

    @GetMapping("/orfaos")
    public ResponseEntity<OrfaosDTO> orfaos() {
        return ResponseEntity.ok(orfaosService.verificar());
    }

    @PostMapping("/orfaos/remover")
    public ResponseEntity<OrfaosDTO> removerOrfaos() {
        return ResponseEntity.ok(orfaosService.remover());
    }
}
