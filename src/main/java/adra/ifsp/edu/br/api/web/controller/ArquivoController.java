package adra.ifsp.edu.br.api.web.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import adra.ifsp.edu.br.api.domain.dto.arquivo.ArquivoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.arquivo.UrlArquivoDTO;
import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.service.ArquivoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/arquivos")
@RequiredArgsConstructor
public class ArquivoController {

    private final ArquivoService arquivoService;

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArquivoResponseDTO> enviar(@RequestParam EntidadeArquivo entidadeTipo,
                                                     @RequestParam Long entidadeId,
                                                     @RequestParam CategoriaArquivo categoria,
                                                     @RequestPart("arquivo") MultipartFile arquivo) {
        ArquivoResponseDTO salvo = arquivoService.salvar(entidadeTipo, entidadeId, categoria, arquivo);
        return ResponseEntity.created(URI.create("/api/arquivos/" + salvo.arquivoId())).body(salvo);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping
    public ResponseEntity<List<ArquivoResponseDTO>> listar(@RequestParam EntidadeArquivo entidadeTipo,
                                                           @RequestParam Long entidadeId,
                                                           @RequestParam(required = false) CategoriaArquivo categoria) {
        return ResponseEntity.ok(arquivoService.listar(entidadeTipo, entidadeId, categoria));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO')")
    @GetMapping("/{id}/url")
    public ResponseEntity<UrlArquivoDTO> gerarUrl(@PathVariable Long id) {
        return ResponseEntity.ok(arquivoService.gerarUrl(id));
    }

    @PreAuthorize("hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        arquivoService.remover(id);
        return ResponseEntity.noContent().build();
    }
}