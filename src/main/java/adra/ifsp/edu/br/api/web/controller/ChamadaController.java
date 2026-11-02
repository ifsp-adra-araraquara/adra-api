package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadaGrupoDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadasDoDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.PresencaChamadaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.StatusChamadaDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.FrequenciaDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.GradeMensalTurmaDTO;
import adra.ifsp.edu.br.api.domain.service.ChamadaService;
import adra.ifsp.edu.br.api.domain.service.FrequenciaService;
import adra.ifsp.edu.br.api.domain.service.StatusChamadaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// Chamada por grupo e dia: Coordenador e Educador Social lançam; Assistente Social só lê.
// Permissão por método: não volte a pôr @PreAuthorize na classe, senão abre/fecha tudo junto.
@RestController
@RequestMapping("/api/chamadas")
@RequiredArgsConstructor
public class ChamadaController {

    private final ChamadaService chamadaService;
    private final FrequenciaService frequenciaService;
    private final StatusChamadaService statusChamadaService;

    /** Aba "Chamadas" da ficha do atendido — histórico de frequência por chamada lançada. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/assistido/{idAssistido}/frequencia")
    public ResponseEntity<List<FrequenciaDiaDTO>> buscarFrequenciaAssistido(@PathVariable Long idAssistido) {
        return ResponseEntity.ok(frequenciaService.historicoDoAtendido(idAssistido));
    }

    /**
     * Grade mensal do grupo no formato da planilha, um dia por chamada — `mes` no formato
     * yyyy-MM; sem ele, o mês atual.
     */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL') "
            + "or (hasRole('OFICINEIRO') and @acessoTurma.podeAcessarTurma(#idTurma))")
    @GetMapping("/turma/{idTurma}/grade")
    public ResponseEntity<GradeMensalTurmaDTO> buscarGradeMensalTurma(
            @PathVariable Long idTurma,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes
    ) {
        return ResponseEntity.ok(frequenciaService.gradeMensal(idTurma, mes != null ? mes : YearMonth.now()));
    }

    /** FT-12: grupos com chamada no dia e se ela já foi lançada. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/dia")
    public ResponseEntity<ChamadasDoDiaDTO> chamadasDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        return ResponseEntity.ok(chamadaService.chamadasDoDia(data));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/turma/{idTurma}/dia/{data}")
    public ResponseEntity<ChamadaGrupoDTO> buscarChamadaDoGrupo(
            @PathVariable Long idTurma,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        return ResponseEntity.ok(chamadaService.buscar(idTurma, data));
    }

    /** Lançadas e pendentes do período, grupo a grupo e dia a dia (relatório e aba Pendentes). */
    @PreAuthorize("hasRole('COORDENADOR')")
    @GetMapping("/status")
    public ResponseEntity<List<StatusChamadaDiaDTO>> statusNoPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        return ResponseEntity.ok(statusChamadaService.noPeriodo(inicio, fim));
    }

    /** Grava a chamada inteira do grupo no dia; regravar atualiza a mesma chamada. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @PutMapping("/turma/{idTurma}/dia/{data}")
    public ResponseEntity<ChamadaGrupoDTO> gravarChamadaDoGrupo(
            @PathVariable Long idTurma,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestBody List<@Valid PresencaChamadaRequestDTO> presencas
    ) {
        return ResponseEntity.ok(chamadaService.gravar(idTurma, data, presencas));
    }
}
