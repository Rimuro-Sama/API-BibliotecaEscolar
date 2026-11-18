package io.github.rimurosama.biblioteca.api.erro;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Respostas de erro no formato padrão RFC 7807 (application/problem+json), sem vazar detalhes internos.
 *
 * Estende {@link ResponseEntityExceptionHandler} para que os erros comuns do Spring MVC (rota inexistente = 404,
 * método não permitido = 405, tipo de mídia = 415 etc.) mantenham o status correto, em vez de virarem 500.
 */
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(Erros.ErroDeNegocio.class)
    ResponseEntity<Object> negocio(Erros.ErroDeNegocio e, WebRequest request) {
        return problema(e, e.status(), e.status().getReasonPhrase(), e.getMessage(), null, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return problema(e, HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.", campos, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return problema(e, HttpStatus.BAD_REQUEST, "Requisição inválida",
                "O corpo da requisição não pôde ser interpretado (JSON malformado ou com tipos errados).", null, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> parametros(ConstraintViolationException e, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> campos.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return problema(e, HttpStatus.BAD_REQUEST, "Parâmetros inválidos", "Um ou mais parâmetros são inválidos.", campos, request);
    }

    /** Rede de segurança: o banco recusou algo que a validação do código não pegou (ex.: corrida entre requisições). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integridade(DataIntegrityViolationException e, WebRequest request) {
        log.warn("Violação de integridade: {}", e.getMostSpecificCause().getMessage());
        return problema(e, HttpStatus.CONFLICT, "Conflito", "A operação conflita com os dados existentes.", null, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception e, WebRequest request) {
        log.error("Erro inesperado", e);
        return problema(e, HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado.", null, request);
    }

    private ResponseEntity<Object> problema(Exception e, HttpStatus status, String titulo, String detalhe,
                                            Map<String, String> campos, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setTitle(titulo);
        if (campos != null) pd.setProperty("campos", campos);
        return handleExceptionInternal(e, pd, new HttpHeaders(), status, request);
    }
}
