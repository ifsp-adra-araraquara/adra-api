package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ArquivarRegistroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PendenciasDisciplinaresDTO;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.DocumentoDevolvidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.FichaDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResolverConsequenciaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.SinalizacaoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.service.DisciplinarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * FT-04: sistema disciplinar. Coordenador e Sociopedagógico registram e
 * consultam; arquivar e cadastrar motivos é só do Coordenador. O Oficineiro
 * não acessa (dado sensível, DT-01).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
public class DisciplinarController {

    private final DisciplinarService disciplinarService;

    // ---- Motivos ----

    @GetMapping("/disciplinar/motivos")
    public ResponseEntity<List<MotivoDisciplinarResponseDTO>> listarMotivos(
            @RequestParam(defaultValue = "false") boolean incluirInativos
    ) {
        return ResponseEntity.ok(disciplinarService.listarMotivos(incluirInativos));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping("/disciplinar/motivos")
    public ResponseEntity<MotivoDisciplinarResponseDTO> criarMotivo(@Valid @RequestBody MotivoDisciplinarRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disciplinarService.criarMotivo(dto));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PutMapping("/disciplinar/motivos/{motivoId}")
    public ResponseEntity<MotivoDisciplinarResponseDTO> atualizarMotivo(
            @PathVariable Long motivoId,
            @Valid @RequestBody MotivoDisciplinarRequestDTO dto
    ) {
        return ResponseEntity.ok(disciplinarService.atualizarMotivo(motivoId, dto));
    }

    // ---- Tela Disciplinar ----

    @GetMapping("/disciplinar/registros")
    public ResponseEntity<PaginaDTO<RegistroDisciplinarResponseDTO>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) NivelDisciplinar nivel,
            @RequestParam(defaultValue = "false") boolean somentePendencias,
            @RequestParam(defaultValue = "false") boolean incluirArquivados,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return ResponseEntity.ok(disciplinarService.listar(busca, nivel, somentePendencias, incluirArquivados,
                Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));
    }

    @GetMapping("/disciplinar/pendencias")
    public ResponseEntity<PendenciasDisciplinaresDTO> buscarPendencias() {
        return ResponseEntity.ok(disciplinarService.buscarPendencias());
    }

    // ---- Ficha do assistido ----

    @GetMapping("/assistidos/{assistidoId}/disciplinar")
    public ResponseEntity<FichaDisciplinarDTO> buscarFicha(@PathVariable Long assistidoId) {
        return ResponseEntity.ok(disciplinarService.buscarFicha(assistidoId));
    }

    /** Devolve o registro lançado e os gerados pela progressão (advertência/suspensão automáticas). */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @PostMapping("/assistidos/{assistidoId}/disciplinar")
    public ResponseEntity<List<RegistroDisciplinarResponseDTO>> registrar(
            @PathVariable Long assistidoId,
            @Valid @RequestBody RegistroDisciplinarRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disciplinarService.registrar(assistidoId, dto));
    }

    // ---- Registro ----

    @GetMapping("/disciplinar/{registroId}")
    public ResponseEntity<RegistroDisciplinarResponseDTO> buscarRegistro(@PathVariable Long registroId) {
        return ResponseEntity.ok(disciplinarService.buscarRegistro(registroId));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/disciplinar/{registroId}/documento-devolvido")
    public ResponseEntity<RegistroDisciplinarResponseDTO> marcarDocumentoDevolvido(
            @PathVariable Long registroId,
            @RequestBody(required = false) DocumentoDevolvidoRequestDTO dto
    ) {
        return ResponseEntity.ok(disciplinarService.marcarDocumentoDevolvido(registroId, dto));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/disciplinar/{registroId}/suspensao")
    public ResponseEntity<RegistroDisciplinarResponseDTO> alterarPeriodoSuspensao(
            @PathVariable Long registroId,
            @Valid @RequestBody PeriodoSuspensaoRequestDTO dto
    ) {
        return ResponseEntity.ok(disciplinarService.alterarPeriodoSuspensao(registroId, dto));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/disciplinar/{registroId}/consequencia")
    public ResponseEntity<RegistroDisciplinarResponseDTO> resolverConsequencia(
            @PathVariable Long registroId,
            @Valid @RequestBody ResolverConsequenciaRequestDTO dto
    ) {
        return ResponseEntity.ok(disciplinarService.resolverConsequencia(registroId, dto));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/disciplinar/{registroId}/arquivar")
    public ResponseEntity<RegistroDisciplinarResponseDTO> arquivar(
            @PathVariable Long registroId,
            @Valid @RequestBody ArquivarRegistroRequestDTO dto
    ) {
        return ResponseEntity.ok(disciplinarService.arquivar(registroId, dto));
    }

    // ---- Sinalizações (chamada e listagem) ----

    /** Ex.: GET /api/disciplinar/sinalizacoes?assistidoIds=1,2,3&data=2026-10-24 (sem data = hoje). */
    @GetMapping("/disciplinar/sinalizacoes")
    public ResponseEntity<List<SinalizacaoDisciplinarDTO>> buscarSinalizacoes(
            @RequestParam List<Long> assistidoIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        return ResponseEntity.ok(disciplinarService.buscarSinalizacoes(assistidoIds, data != null ? data : LocalDate.now()));
    }
}
