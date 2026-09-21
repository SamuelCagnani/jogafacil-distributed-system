package com.jogafacil.contracts.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class ApiProblemsTest {

    @Test
    void buildsAProblemDetailCarryingTheDomainCode() {
        ProblemDetail problem = ApiProblems.of(
                ProblemCode.RESERVATION_CONFLICT, HttpStatus.CONFLICT, "slot already reserved");

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("RESERVATION_CONFLICT");
        assertThat(problem.getDetail()).isEqualTo("slot already reserved");
        assertThat(problem.getProperties()).containsEntry("code", "RESERVATION_CONFLICT");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }
}
