package adra.ifsp.edu.br.api.web.controller;

import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelResponseDTO;
import adra.ifsp.edu.br.api.domain.service.ResponsavelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/responsaveis")
@RequiredArgsConstructor
public class ResponsavelController {

    private final ResponsavelService responsavelService;

    @PostMapping
    public ResponseEntity<ResponsavelResponseDTO> cadastrar(@Valid @RequestBody ResponsavelRequestDTO dto) {
        ResponsavelResponseDTO criado = responsavelService.cadastrar(dto);
        return ResponseEntity.created(URI.create("/api/responsaveis/" + criado.responsavelId())).body(criado);
    }

    @GetMapping
    public ResponseEntity<List<ResponsavelResponseDTO>> listar() {
        return ResponseEntity.ok(responsavelService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponsavelResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(responsavelService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponsavelResponseDTO> atualizar(@PathVariable Long id,
                                                              @Valid @RequestBody ResponsavelRequestDTO dto) {
        return ResponseEntity.ok(responsavelService.atualizar(id, dto));
    }

    // Propositalmente sem DELETE: o schema atual nao tem coluna de
    // status/ativo para responsavel, entao remover aqui seria hard delete e
    // cascatearia a exclusao de TODOS os vinculos familiares dele (ON DELETE
    // CASCADE em assistido_responsavel). Decisao de produto pendente com a
    // Tatiane/ADRA antes de implementar - por ora, gerenciar via vinculo.
}
