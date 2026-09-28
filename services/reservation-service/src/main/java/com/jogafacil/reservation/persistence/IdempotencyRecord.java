package com.jogafacil.reservation.persistence;

/**
 * Registro de idempotencia associado a uma {@code Idempotency-Key}.
 *
 * @param operationId   chave enviada pelo cliente
 * @param requestHash   hash do corpo original
 * @param responseStatus status HTTP da resposta original ({@code null} enquanto pendente)
 * @param responseBody  corpo JSON da resposta original ({@code null} enquanto pendente)
 */
public record IdempotencyRecord(
        String operationId,
        String requestHash,
        Integer responseStatus,
        String responseBody) {
}
