package adra.ifsp.edu.br.api.web.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import adra.ifsp.edu.br.api.domain.dto.PaginaDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoContagemDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.ElegibilidadeDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.ResultadoLinhaLoteDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.VinculoTurmaAssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.service.AssistidoLoteService;
import adra.ifsp.edu.br.api.domain.service.AssistidoService;
import adra.ifsp.edu.br.api.domain.service.ElegibilidadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assistidos")
@RequiredArgsConstructor
public class AssistidoController {

    private final AssistidoService assistidoService;
    private final AssistidoLoteService assistidoLoteService;
    private final ElegibilidadeService elegibilidadeService;

    /** Critérios de entrada (ex.: limite de renda familiar) para o formulário avisar antes de salvar. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping("/elegibilidade")
    public ResponseEntity<ElegibilidadeDTO> elegibilidade() {
        return ResponseEntity.ok(elegibilidadeService.criterios());
    }

//    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
//    @PostMapping
//    public ResponseEntity<AssistidoResponseDTO> cadastrar(@Valid @RequestBody AssistidoRequestDTO dto) {
//        AssistidoResponseDTO criado = assistidoService.cadastrar(dto);
//        return ResponseEntity.created(URI.create("/api/assistidos/" + criado.assistidoId())).body(criado);
//    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssistidoResponseDTO> cadastrar(@Valid @RequestPart("dados") AssistidoRequestDTO dto,
                                                          @RequestPart("foto") MultipartFile foto,
                                                          @RequestPart("documentos") List<MultipartFile> documentos) {
        AssistidoResponseDTO criado = assistidoService.cadastrarComArquivos(dto, foto, documentos);
        return ResponseEntity.created(URI.create("/api/assistidos/" + criado.assistidoId())).body(criado);
    }

    // Sem @Valid no corpo: cada linha e' validada no service para o lote seguir com as demais.
    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PostMapping("/lote/validar")
    public ResponseEntity<List<ResultadoLinhaLoteDTO>> validarLote(@RequestBody List<AssistidoRequestDTO> itens) {
        return ResponseEntity.ok(assistidoLoteService.validar(itens));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PostMapping("/lote")
    public ResponseEntity<List<ResultadoLinhaLoteDTO>> cadastrarLote(@RequestBody List<AssistidoRequestDTO> itens) {
        return ResponseEntity.ok(assistidoLoteService.cadastrar(itens));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO', 'OFICINEIRO')")
    @GetMapping
    public ResponseEntity<PaginaDTO<AssistidoResponseDTO>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) Long oficinaId,
            @RequestParam(required = false) StatusGeral status,
            @RequestParam(defaultValue = "false") boolean emAcompanhamento,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok(assistidoService.listarPaginado(
                busca, turmaId, oficinaId, status, emAcompanhamento, pagina, tamanho));
    }

    /** Totais das abas da listagem (Todos / Ativos / Inativos / Em acompanhamento). */
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO', 'OFICINEIRO')")
    @GetMapping("/contagem")
    public ResponseEntity<AssistidoContagemDTO> contar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) Long oficinaId) {
        return ResponseEntity.ok(assistidoService.contar(busca, turmaId, oficinaId));
    }

    /**
     * Endpoint leve para o alerta NAO BLOQUEANTE de duplicidade por nome
     * e data de nascimento no formulario do front (CA-A02.2).
     */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping("/verificar-duplicidade")
    public ResponseEntity<Map<String, Object>> verificarDuplicidade(
            @RequestParam(required = false) String nomeCompleto,
            @RequestParam(required = false) String dataNascimento
    ) {
        if (nomeCompleto == null || dataNascimento == null) {
            return ResponseEntity.ok(Map.of(
                    "possivelDuplicidade", false,
                    "duplicados", List.of()
            ));
        }
        return ResponseEntity.ok(assistidoService.verificarDuplicidade(nomeCompleto, dataNascimento));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping("/{id}")
    public ResponseEntity<AssistidoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(assistidoService.buscarPorId(id));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PutMapping("/{id}")
    public ResponseEntity<AssistidoResponseDTO> atualizar(@PathVariable Long id,
                                                            @Valid @RequestBody AssistidoRequestDTO dto) {
        return ResponseEntity.ok(assistidoService.atualizar(id, dto));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<AssistidoResponseDTO> alterarStatus(@PathVariable Long id,
                                                                @Valid @RequestBody AssistidoStatusRequestDTO dto) {
        return ResponseEntity.ok(assistidoService.alterarStatus(id, dto));
    }

    /** Aba "Turmas" do modal do assistido — histórico de vínculos (turma_aluno), só pro coordenador. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping("/{id}/turmas")
    public ResponseEntity<List<VinculoTurmaAssistidoResponseDTO>> listarHistoricoTurmas(@PathVariable Long id) {
        return ResponseEntity.ok(assistidoService.listarHistoricoTurmas(id));
    }
}
