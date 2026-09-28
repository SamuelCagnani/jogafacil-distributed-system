package com.jogafacil.reservation.domain;

import com.jogafacil.reservation.api.dto.ReservationResponse;

/**
 * Resultado de uma criacao idempotente: o status HTTP e o corpo a devolver.
 *
 * <p>{@code status} e {@code 201} na primeira execucao e {@code 200} quando a
 * resposta e um replay de uma operacao anterior com a mesma
 * {@code Idempotency-Key}.</p>
 */
public record ReservationResult(int status, ReservationResponse body) {
}
