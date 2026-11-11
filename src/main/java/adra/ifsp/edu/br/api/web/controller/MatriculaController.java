package adra.ifsp.edu.br.api.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.matricula.EncerramentoAnoDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.EncerramentoPreviaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.PeriodoMatriculaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.PeriodoMatriculaRequestDTO;
import adra.ifsp.edu.br.api.domain.service.EncerramentoAnoService;
import adra.ifsp.edu.br.api.domain.service.PeriodoMatriculaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/matriculas")
@RequiredArgsConstructor
public class MatriculaController {

    private final PeriodoMatriculaService periodoMatriculaService;
    private final EncerramentoAnoService encerramentoAnoService;

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

    /** Ano esperando encerramento, com a prévia; 204 quando não há. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/encerramento-pendente")
    public ResponseEntity<EncerramentoPreviaDTO> encerramentoPendente() {
        return encerramentoAnoService.pendente()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/{ano}/encerramento")
    public ResponseEntity<EncerramentoPreviaDTO> previaDoEncerramento(@PathVariable int ano) {
        return ResponseEntity.ok(encerramentoAnoService.previa(ano));
    }

    /** Encerra o ano: quem não renovou perde a vaga e quem renovou passa para o ano seguinte (só o Coordenador). */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping("/{ano}/encerrar")
    public ResponseEntity<EncerramentoAnoDTO> encerrar(@PathVariable int ano) {
        return ResponseEntity.ok(encerramentoAnoService.encerrar(ano));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/encerramentos")
    public ResponseEntity<List<EncerramentoAnoDTO>> encerramentos() {
        return ResponseEntity.ok(encerramentoAnoService.listar());
    }
}
