package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ArquivarRegistroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PendenciasDisciplinaresDTO;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.DocumentoDevolvidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.FichaDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.GerarPelaRecomendacaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.MotivoDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.PeriodoSuspensaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroCriadoDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroVariosRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResolverConsequenciaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.SinalizacaoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ZerarContagemRequestDTO;
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
 * FT-04: sistema disciplinar. Coordenador, Assistente Social e Educador Social
 * consultam. Ocorrência é registrada pelo Educador Social (e pelo Coordenador);
 * de advertência em diante é só o Coordenador — a regra de nível fica no
 * DisciplinarService. Arquivar e cadastrar motivos é só do Coordenador. O
 * Oficineiro não acessa (dado sensível, DT-01).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
// Leitura liberada para Coordenação, Assistente Social e Educador Social; o que altera registro tem
// @PreAuthorize próprio no método.
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
            @RequestParam(defaultValue = "false") boolean somenteRecomendacoes,
            @RequestParam(defaultValue = "false") boolean incluirArquivados,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return ResponseEntity.ok(disciplinarService.listar(busca, nivel, somentePendencias, somenteRecomendacoes, incluirArquivados,
                Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));
    }

    /** FT-22: um fato com vários atendidos — um registro por atendido, tudo ou nada. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @PostMapping("/disciplinar/registros")
    public ResponseEntity<List<RegistroCriadoDTO>> registrarVarios(@Valid @RequestBody RegistroVariosRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disciplinarService.registrarVarios(dto));
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

    /** Devolve o registro lançado e a recomendação pendente do assistido, se houver. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'EDUCADOR_SOCIAL')")
    @PostMapping("/assistidos/{assistidoId}/disciplinar")
    public ResponseEntity<RegistroCriadoDTO> registrar(
            @PathVariable Long assistidoId,
            @Valid @RequestBody RegistroDisciplinarRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disciplinarService.registrar(assistidoId, dto));
    }

    /**
     * Período padrão de uma suspensão pelo fato na data, em dias de atividade do grupo do atendido:
     * ex.: GET /api/assistidos/7/disciplinar/periodo-suspensao?data=2026-11-05 (quinta) → sexta, segunda e terça.
     */
    @PreAuthorize("hasRole('COORDENADOR')")
    @GetMapping("/assistidos/{assistidoId}/disciplinar/periodo-suspensao")
    public ResponseEntity<PeriodoSuspensaoDTO> sugerirPeriodoSuspensao(
            @PathVariable Long assistidoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        return ResponseEntity.ok(disciplinarService.sugerirPeriodoSuspensao(assistidoId, data));
    }

    /** FT-24: arquiva os registros ativos (de um nível ou de todos) e devolve a ficha atualizada. */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping("/assistidos/{assistidoId}/disciplinar/zerar")
    public ResponseEntity<FichaDisciplinarDTO> zerarContagem(
            @PathVariable Long assistidoId,
            @Valid @RequestBody ZerarContagemRequestDTO dto
    ) {
        return ResponseEntity.ok(disciplinarService.zerarContagem(assistidoId, dto));
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

    /** FT-22: gera a advertência/suspensão recomendada pelo registro. Dispensar é o PATCH de consequência. */
    @PreAuthorize("hasRole('COORDENADOR')")
    @PostMapping("/disciplinar/{registroId}/recomendacao")
    public ResponseEntity<RegistroCriadoDTO> gerarPelaRecomendacao(
            @PathVariable Long registroId,
            @Valid @RequestBody GerarPelaRecomendacaoRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disciplinarService.gerarPelaRecomendacao(registroId, dto));
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

    /**
     * Ex.: GET /api/disciplinar/sinalizacoes?assistidoIds=1,2,3&data=2026-10-24 (sem data = hoje).
     * Único endpoint do disciplinar aberto ao Educador Social: o selo "Suspenso" dentro da chamada.
     */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL', 'EDUCADOR_SOCIAL')")
    @GetMapping("/disciplinar/sinalizacoes")
    public ResponseEntity<List<SinalizacaoDisciplinarDTO>> buscarSinalizacoes(
            @RequestParam List<Long> assistidoIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        return ResponseEntity.ok(disciplinarService.buscarSinalizacoes(assistidoIds, data != null ? data : LocalDate.now()));
    }
}
