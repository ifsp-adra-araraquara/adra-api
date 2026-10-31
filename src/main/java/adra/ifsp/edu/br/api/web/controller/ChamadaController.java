package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.presenca.FrequenciaAulaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.GradeMensalTurmaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.service.PresencaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

// CA-65.5: só Coordenador e Sociopedagógico lançam/editam chamada.
// O Oficineiro (agora com login, turma.oficineiro_id) só CONSULTA a chamada e a
// grade das turmas em que é o responsável — regra no AcessoTurmaService (403 nas outras).
// Permissão por método: não volte a pôr @PreAuthorize na classe, senão abre/fecha tudo junto.
@RestController
@RequestMapping("/api/chamadas")
@RequiredArgsConstructor
public class ChamadaController {

    private final PresencaService presencaService;

    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @PostMapping
    public ResponseEntity<PresencaResponseDTO> registrarPresenca(
            @Valid @RequestBody PresencaRequestDTO presencaRequestDTO
    ) {
        return ResponseEntity.ok(presencaService.registrarPresenca(presencaRequestDTO));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @PostMapping("/aula/{idAula}")
    public ResponseEntity<List<PresencaResponseDTO>> registrarChamada(
            @PathVariable Long idAula,
            @RequestBody List<PresencaRequestDTO> presencas
    ) {
        return ResponseEntity.ok(presencaService.registrarChamada(idAula, presencas));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL') "
            + "or (hasRole('OFICINEIRO') and @acessoTurma.podeAcessarAula(#idAula))")
    @GetMapping("/aula/{idAula}")
    public ResponseEntity<List<PresencaResponseDTO>> buscarPorAula(@PathVariable Long idAula) {
        return ResponseEntity.ok(presencaService.buscarPorAula(idAula));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/assistido/{idAssistido}")
    public ResponseEntity<List<PresencaResponseDTO>> buscarPorAssistido(@PathVariable Long idAssistido) {
        return ResponseEntity.ok(presencaService.buscarPorAssistido(idAssistido));
    }

    /** Aba "Chamadas" do cadastro do assistido — histórico de frequência completo (US novo, ver PresencaService). */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/assistido/{idAssistido}/frequencia")
    public ResponseEntity<List<FrequenciaAulaDTO>> buscarFrequenciaAssistido(@PathVariable Long idAssistido) {
        return ResponseEntity.ok(presencaService.buscarFrequenciaAssistido(idAssistido));
    }

    /**
     * Grade mensal da turma no formato da planilha (FT-06) — `mes` no formato
     * yyyy-MM; sem ele, o mês atual.
     */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL') "
            + "or (hasRole('OFICINEIRO') and @acessoTurma.podeAcessarTurma(#idTurma))")
    @GetMapping("/turma/{idTurma}/grade")
    public ResponseEntity<GradeMensalTurmaDTO> buscarGradeMensalTurma(
            @PathVariable Long idTurma,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes
    ) {
        return ResponseEntity.ok(presencaService.buscarGradeMensalTurma(idTurma, mes != null ? mes : YearMonth.now()));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @PutMapping("/{id}")
    public ResponseEntity<PresencaResponseDTO> atualizarPresenca(
            @PathVariable Long id,
            @Valid @RequestBody PresencaRequestDTO presencaRequestDTO
    ) {
        return ResponseEntity.ok(presencaService.atualizarPresenca(id, presencaRequestDTO));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPresenca(@PathVariable Long id) {
        presencaService.deletarPresenca(id);
        return ResponseEntity.noContent().build();
    }
}
