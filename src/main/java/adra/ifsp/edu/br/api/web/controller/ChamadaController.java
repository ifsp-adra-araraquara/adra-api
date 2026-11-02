package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadaGrupoDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadasDoDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.PresencaChamadaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.StatusChamadaDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.FrequenciaDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.GradeMensalTurmaDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.service.ChamadaService;
import adra.ifsp.edu.br.api.domain.service.FrequenciaService;
import adra.ifsp.edu.br.api.domain.service.StatusChamadaService;
import adra.ifsp.edu.br.api.domain.service.PresencaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    private final ChamadaService chamadaService;
    private final FrequenciaService frequenciaService;
    private final StatusChamadaService statusChamadaService;

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
