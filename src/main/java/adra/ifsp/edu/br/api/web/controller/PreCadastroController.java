package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.arquivo.UrlArquivoDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.AnexosPreCadastro;
import adra.ifsp.edu.br.api.domain.dto.precadastro.ObservacaoPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroResumoDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.ReprovarPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusPreCadastro;
import adra.ifsp.edu.br.api.domain.service.PreCadastroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FT-11: link público de pré-cadastro. POST é aberto (sem login — ver
 * SecurityConfig); o resto só Coordenação/Assistente Social.
 */
@RestController
@RequestMapping("/api/pre-cadastro")
@RequiredArgsConstructor
public class PreCadastroController {

    private final PreCadastroService service;

    /** Multipart: "dados" (JSON) + comprovante de endereço + foto e documentos opcionais. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Long>> registrarPublico(
            @Valid @RequestPart("dados") PreCadastroRequestDTO dto,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "documentosCrianca", required = false) List<MultipartFile> documentosCrianca,
            @RequestPart(value = "documentosResponsavel", required = false) List<MultipartFile> documentosResponsavel,
            @RequestPart(value = "comprovanteEndereco", required = false) MultipartFile comprovanteEndereco,
            @RequestParam(value = "captcha", required = false) String captcha,
            HttpServletRequest request
    ) {
        AnexosPreCadastro anexos = new AnexosPreCadastro(foto, documentosCrianca, documentosResponsavel, comprovanteEndereco);
        Long protocolo = service.registrarPublico(dto, anexos, captcha, ipDoCliente(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("preCadastroId", protocolo));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping
    public ResponseEntity<List<PreCadastroResumoDTO>> listar(
            @RequestParam(defaultValue = "PENDENTE") StatusPreCadastro status
    ) {
        return ResponseEntity.ok(service.listar(status));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/{preCadastroId}")
    public ResponseEntity<PreCadastroResponseDTO> buscar(@PathVariable Long preCadastroId) {
        return ResponseEntity.ok(service.abrir(preCadastroId));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/{preCadastroId}/arquivos/{arquivoId}/url")
    public ResponseEntity<UrlArquivoDTO> urlDoArquivo(@PathVariable Long preCadastroId, @PathVariable Long arquivoId) {
        return ResponseEntity.ok(service.gerarUrlDoArquivo(preCadastroId, arquivoId));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/{preCadastroId}/rascunho-assistido")
    public ResponseEntity<AssistidoRequestDTO> rascunhoAssistido(@PathVariable Long preCadastroId) {
        return ResponseEntity.ok(service.montarRascunho(preCadastroId));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @PostMapping("/{preCadastroId}/aprovar")
    public ResponseEntity<PreCadastroResponseDTO> aprovar(
            @PathVariable Long preCadastroId,
            @Valid @RequestBody AssistidoRequestDTO dto
    ) {
        return ResponseEntity.ok(service.aprovar(preCadastroId, dto));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @PatchMapping("/{preCadastroId}/reprovar")
    public ResponseEntity<PreCadastroResponseDTO> reprovar(
            @PathVariable Long preCadastroId,
            @Valid @RequestBody ReprovarPreCadastroRequestDTO dto
    ) {
        return ResponseEntity.ok(service.reprovar(preCadastroId, dto));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @PatchMapping("/{preCadastroId}/lista-espera")
    public ResponseEntity<PreCadastroResponseDTO> colocarNaListaEspera(
            @PathVariable Long preCadastroId,
            @Valid @RequestBody ObservacaoPreCadastroRequestDTO dto
    ) {
        return ResponseEntity.ok(service.colocarNaListaEspera(preCadastroId, dto));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @PatchMapping("/{preCadastroId}/observacao")
    public ResponseEntity<Void> salvarObservacao(
            @PathVariable Long preCadastroId,
            @Valid @RequestBody ObservacaoPreCadastroRequestDTO dto
    ) {
        service.salvarObservacao(preCadastroId, dto);
        return ResponseEntity.noContent().build();
    }

    private String ipDoCliente(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
