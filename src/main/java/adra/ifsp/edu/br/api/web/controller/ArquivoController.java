package adra.ifsp.edu.br.api.web.controller;

import java.net.URI;
import java.util.List;

import adra.ifsp.edu.br.api.domain.dto.arquivo.AulaArquivoDTO;
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

/**
 * Permissões:
 * - Assistido/responsável/usuário: Coordenador e Sociopedagógico (como antes).
 * - Material de AULA:
 *   - Oficineiro responsável pela turma ENVIA; vê, abre e exclui SÓ os que ele enviou
 *     (a listagem é filtrada no ArquivoService; abrir/excluir de outro = 403).
 *   - Coordenador/Sociopedagógico/Admin VEEM todos (com quem enviou), mas não enviam nem excluem.
 *   Regras no AcessoTurmaService (bean "acessoTurma").
 */
@RestController
@RequestMapping("/api/arquivos")
@RequiredArgsConstructor
public class ArquivoController {

    private final ArquivoService arquivoService;

    @PreAuthorize("(hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO') and #entidadeTipo != T(adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo).AULA) "
            + "or (hasRole('OFICINEIRO') and #entidadeTipo == T(adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo).AULA and @acessoTurma.podeAcessarAula(#entidadeId))")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArquivoResponseDTO> enviar(@RequestParam EntidadeArquivo entidadeTipo,
                                                     @RequestParam Long entidadeId,
                                                     @RequestParam CategoriaArquivo categoria,
                                                     @RequestPart("arquivo") MultipartFile arquivo) {
        ArquivoResponseDTO salvo = arquivoService.salvar(entidadeTipo, entidadeId, categoria, arquivo);
        return ResponseEntity.created(URI.create("/api/arquivos/" + salvo.arquivoId())).body(salvo);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO') "
            + "or (hasRole('OFICINEIRO') and #entidadeTipo == T(adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo).AULA and @acessoTurma.podeAcessarAula(#entidadeId))")
    @GetMapping
    public ResponseEntity<List<ArquivoResponseDTO>> listar(@RequestParam EntidadeArquivo entidadeTipo,
                                                           @RequestParam Long entidadeId,
                                                           @RequestParam(required = false) CategoriaArquivo categoria) {
        return ResponseEntity.ok(arquivoService.listar(entidadeTipo, entidadeId, categoria));
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO') "
            + "or (hasRole('OFICINEIRO') and @acessoTurma.ehAutorDoArquivoDeAula(#id))")
    @GetMapping("/{id}/url")
    public ResponseEntity<UrlArquivoDTO> gerarUrl(@PathVariable Long id) {
        return ResponseEntity.ok(arquivoService.gerarUrl(id));
    }

    @PreAuthorize("(hasAnyRole('COORDENADOR', 'SOCIOPEDAGOGICO') and !@acessoTurma.ehArquivoDeAula(#id)) "
            + "or (hasRole('OFICINEIRO') and @acessoTurma.ehAutorDoArquivoDeAula(#id))")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        arquivoService.remover(id);
        return ResponseEntity.noContent().build();
    }


    /**
     * Materiais da turma agrupados por aula: GET /api/arquivos/turma/{turmaId}/materiais.
     * Caminho próprio: um 2º @GetMapping sem path colidia com o listar() e a API não subia.
     * Oficineiro só da turma em que é o responsável (403 nas outras).
     */
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDENADOR', 'SOCIOPEDAGOGICO') "
            + "or (hasRole('OFICINEIRO') and @acessoTurma.podeAcessarTurma(#turmaId))")
    @GetMapping("/turma/{turmaId}/materiais")
    public ResponseEntity<List<AulaArquivoDTO>> listarArquivosPorAula(@PathVariable Long turmaId) {
        return ResponseEntity.ok(arquivoService.listarArquivosPorAula(turmaId));
    }
}