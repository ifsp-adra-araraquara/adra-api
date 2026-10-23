package adra.ifsp.edu.br.api.exception;

import adra.ifsp.edu.br.api.domain.dto.comum.ErroRespostaDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import adra.ifsp.edu.br.api.domain.dto.comum.AlertaCpfDuplicadoDTO;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    /**
     * @PreAuthorize nega lancando isso desde o Spring Security 6.3 (nao mais AccessDeniedException direto).
     */
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroRespostaDTO> tratarCorpoInvalido(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErroRespostaDTO.de(400, "Dados invalidos", "Um ou mais campos possuem valor invalido",
                        request.getRequestURI()));
    }

    /**
     * Rede de seguranca: se alguma constraint do banco (ex.: indice unico) escapar da validacao preventiva do service.
     */
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

    /**
     * 409 com o responsavel existente no corpo, para o front oferecer
     * reaproveitar o registro em vez de permitir duplicidade.
     */
    @ExceptionHandler(CpfDuplicadoException.class)
    public ResponseEntity<AlertaCpfDuplicadoDTO> tratarCpfDuplicado(CpfDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AlertaCpfDuplicadoDTO(ex.getMessage(), ex.getResponsavelExistente()));
    }

    @ExceptionHandler(ArquivoInvalidoException.class)
    public ResponseEntity<ErroRespostaDTO> tratarArquivoInvalido(ArquivoInvalidoException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErroRespostaDTO.de(400, "Arquivo invalido", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroRespostaDTO> tratarArquivoGrande(MaxUploadSizeExceededException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(
                ErroRespostaDTO.de(413, "Arquivo muito grande",
                        "O arquivo excede o limite de 3 MB.", request.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErroRespostaDTO> tratarParteAusente(MissingServletRequestPartException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErroRespostaDTO.de(400, "Dados invalidos",
                        "Parte obrigatoria ausente: " + ex.getRequestPartName(), request.getRequestURI()));
    }

    @ExceptionHandler(ArmazenamentoException.class)
    public ResponseEntity<ErroRespostaDTO> tratarArmazenamento(ArmazenamentoException ex,
                                                               HttpServletRequest request) {
        String correlacao = UUID.randomUUID().toString().substring(0, 8);
        log.error("Falha no storage [{}] em {}", correlacao, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ErroRespostaDTO.de(502, "Falha no armazenamento",
                        "Nao foi possivel salvar o arquivo agora. Tente novamente. Codigo " + correlacao,
                        request.getRequestURI()));
    }
}
