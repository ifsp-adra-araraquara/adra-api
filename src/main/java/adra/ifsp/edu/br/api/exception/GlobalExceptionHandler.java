package adra.ifsp.edu.br.api.exception;

import adra.ifsp.edu.br.api.domain.dto.comum.AlertaDuplicidadeDTO;
import adra.ifsp.edu.br.api.domain.dto.comum.ErroRespostaDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntidadeNaoEncontradaException.class)
    public ResponseEntity<ErroRespostaDTO> tratarNaoEncontrado(EntidadeNaoEncontradaException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErroRespostaDTO.de(404, "Nao encontrado", ex.getMessage(), request.getRequestURI()));
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
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErroRespostaDTO.de(500, "Erro interno", "Ocorreu um erro inesperado. Tente novamente.",
                        request.getRequestURI()));
    }
}
