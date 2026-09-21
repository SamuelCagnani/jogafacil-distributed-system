package com.jogafacil.contracts.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/**
 * Fabrica de {@link ProblemDetail} (RFC 9457) com os membros de extensao do
 * contrato JogaFacil: {@code code} (codigo de dominio) e {@code timestamp}.
 */
public final class ApiProblems {

    private ApiProblems() {
    }

    public static ProblemDetail of(ProblemCode code, HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code.name());
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
