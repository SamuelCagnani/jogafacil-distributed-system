package com.jogafacil.contracts.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Converte excecoes em respostas RFC 9457 ({@code application/problem+json})
 * de forma uniforme em todos os servicos.
 *
 * <ul>
 *   <li>{@link ApiException} -&gt; codigo e status de dominio definidos pela propria excecao;</li>
 *   <li>{@link MethodArgumentNotValidException} -&gt; 400 {@code VALIDATION_ERROR};</li>
 *   <li>qualquer outra excecao -&gt; 500 {@code INTERNAL_ERROR}, sem vazar detalhes internos.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException exception) {
        return ApiProblems.of(exception.code(), exception.status(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiProblems.of(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ApiProblems.of(ProblemCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error");
    }
}
