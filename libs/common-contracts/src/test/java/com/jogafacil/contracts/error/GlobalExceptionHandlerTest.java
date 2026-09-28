package com.jogafacil.contracts.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAnApiExceptionToItsProblemCodeAndStatus() {
        ProblemDetail problem = handler.handleApiException(
                ApiException.conflict(ProblemCode.RESERVATION_CONFLICT, "slot already reserved"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "RESERVATION_CONFLICT");
    }

    @Test
    void mapsAnUnexpectedExceptionToInternalError() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
