package adra.ifsp.edu.br.api.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import adra.ifsp.edu.br.api.domain.dto.desligamento.RecusarDesligamentoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.desligamento.SolicitacaoDesligamentoDTO;
import adra.ifsp.edu.br.api.domain.dto.desligamento.SolicitarDesligamentoRequestDTO;
import adra.ifsp.edu.br.api.domain.service.SolicitacaoDesligamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Pedido de desligamento: a Assistente Social pede (e pode retirar o pedido);
 * só o Coordenador aprova ou recusa. Desligar e reativar direto é só do Coordenador
 * (PATCH /api/assistidos/{id}/status).
 */
@RestController
@RequestMapping("/api/assistidos")
@RequiredArgsConstructor
public class SolicitacaoDesligamentoController {

    private final SolicitacaoDesligamentoService service;

    /** Pedidos em aberto e os decididos nos últimos 30 dias. */
    @PreAuthorize("hasAnyRole('COORDENADOR', 'ASSISTENTE_SOCIAL')")
    @GetMapping("/desligamentos")
    public ResponseEntity<List<SolicitacaoDesligamentoDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PreAuthorize("hasRole('ASSISTENTE_SOCIAL')")
    @PostMapping("/{assistidoId}/desligamento")
    public ResponseEntity<SolicitacaoDesligamentoDTO> solicitar(@PathVariable Long assistidoId,
                                                                @Valid @RequestBody SolicitarDesligamentoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.solicitar(assistidoId, dto.dataSaida(), dto.motivo()));
    }

    @PreAuthorize("hasRole('ASSISTENTE_SOCIAL')")
    @PatchMapping("/desligamentos/{solicitacaoId}/cancelar")
    public ResponseEntity<SolicitacaoDesligamentoDTO> cancelar(@PathVariable Long solicitacaoId) {
        return ResponseEntity.ok(service.cancelar(solicitacaoId));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/desligamentos/{solicitacaoId}/aprovar")
    public ResponseEntity<SolicitacaoDesligamentoDTO> aprovar(@PathVariable Long solicitacaoId) {
        return ResponseEntity.ok(service.aprovar(solicitacaoId));
    }

    @PreAuthorize("hasRole('COORDENADOR')")
    @PatchMapping("/desligamentos/{solicitacaoId}/recusar")
    public ResponseEntity<SolicitacaoDesligamentoDTO> recusar(@PathVariable Long solicitacaoId,
                                                              @Valid @RequestBody RecusarDesligamentoRequestDTO dto) {
        return ResponseEntity.ok(service.recusar(solicitacaoId, dto.resposta()));
    }
}
