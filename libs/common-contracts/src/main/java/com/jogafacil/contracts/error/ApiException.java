package com.jogafacil.contracts.error;

import org.springframework.http.HttpStatus;

/**
 * Excecao de dominio que carrega o {@link ProblemCode} e o status HTTP esperado.
 *
 * <p>Os servicos lancam {@code ApiException} para situacoes previsiveis (conflito,
 * validacao, recurso inexistente) e o {@link GlobalExceptionHandler} a converte no
 * corpo RFC 9457 correspondente.</p>
 */
public class ApiException extends RuntimeException {

    private final transient ProblemCode code;
    private final transient HttpStatus status;

    public ApiException(ProblemCode code, HttpStatus status, String detail) {
        super(detail);
        this.code = code;
        this.status = status;
    }

    public ProblemCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException conflict(ProblemCode code, String detail) {
        return new ApiException(code, HttpStatus.CONFLICT, detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(ProblemCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(ProblemCode.NOT_FOUND, HttpStatus.NOT_FOUND, detail);
    }
}
