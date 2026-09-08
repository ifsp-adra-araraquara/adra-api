package adra.ifsp.edu.br.api.exception;

import adra.ifsp.edu.br.api.domain.dto.comum.AlertaDuplicidadeDTO;
import adra.ifsp.edu.br.api.domain.dto.comum.ErroRespostaDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntidadeNaoEncontradaException.class)
    public ResponseEntity<ErroRespostaDTO> tratarNaoEncontrado(EntidadeNaoEncontradaException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErroRespostaDTO.de(404, "Nao encontrado", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroRespostaDTO> tratarAcessoNegado(AcessoNegadoException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErroRespostaDTO.de(403, "Acesso negado", ex.getMessage(), request.getRequestURI()));
    }

    /** @PreAuthorize nega lancando isso desde o Spring Security 6.3 (nao mais AccessDeniedException direto). */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErroRespostaDTO> tratarAutorizacaoNegada(AuthorizationDeniedException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErroRespostaDTO.de(403, "Acesso negado", "Voce nao tem permissao para executar esta acao.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(AutenticacaoException.class)
    public ResponseEntity<ErroRespostaDTO> tratarAutenticacao(AutenticacaoException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErroRespostaDTO.de(401, "Nao autenticado", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroRespostaDTO> tratarRegraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErroRespostaDTO.de(409, "Regra de negocio violada", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 409 com corpo especifico contendo os possiveis duplicados, para o
     * front decidir se reenvia com confirmarApesarDeDuplicidade=true.
     */
    @ExceptionHandler(DuplicidadeProvavelException.class)
    public ResponseEntity<AlertaDuplicidadeDTO> tratarDuplicidade(DuplicidadeProvavelException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AlertaDuplicidadeDTO(ex.getMessage(), ex.getPossiveisDuplicados()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroRespostaDTO> tratarValidacao(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErroRespostaDTO.de(400, "Dados invalidos", "Um ou mais campos estao invalidos",
                        request.getRequestURI(), detalhes));
    }

    /** Rede de seguranca: se alguma constraint do banco (ex.: indice unico) escapar da validacao preventiva do service. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroRespostaDTO> tratarIntegridade(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErroRespostaDTO.de(409, "Violacao de integridade",
                        "A operacao viola uma restricao de integridade do banco de dados.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroRespostaDTO> tratarErroGenerico(Exception ex, HttpServletRequest request) {
        String correlacao = UUID.randomUUID().toString().substring(0, 8);
        log.error("Erro inesperado [{}] em {}", correlacao, request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErroRespostaDTO.interno(
                        "Ocorreu um erro inesperado. Informe o codigo " + correlacao + " ao suporte.",
                        request.getRequestURI(), correlacao));
    }
}
