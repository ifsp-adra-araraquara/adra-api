package adra.ifsp.edu.br.api.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.matricula.PeriodoMatriculaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.PeriodoMatriculaRequestDTO;
import adra.ifsp.edu.br.api.domain.service.PeriodoMatriculaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/matriculas")
@RequiredArgsConstructor
public class MatriculaController {

    private final PeriodoMatriculaService periodoMatriculaService;

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/periodos")
    public ResponseEntity<List<PeriodoMatriculaDTO>> listarPeriodos() {
        return ResponseEntity.ok(periodoMatriculaService.listar());
    }

    /** Abre ou ajusta o período de inscrições do ano (só o Coordenador). */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PutMapping("/periodos/{ano}")
    public ResponseEntity<PeriodoMatriculaDTO> salvarPeriodo(@PathVariable int ano,
                                                             @RequestBody @Valid PeriodoMatriculaRequestDTO dto) {
        return ResponseEntity.ok(periodoMatriculaService.salvar(ano, dto));
    }
}
