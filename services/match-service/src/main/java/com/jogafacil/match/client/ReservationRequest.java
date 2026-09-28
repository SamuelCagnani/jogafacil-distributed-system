package com.jogafacil.match.client;

/**
 * Corpo enviado ao reservation-service em {@code POST /reservations}.
 */
public record ReservationRequest(
        String courtId,
        String slotId,
        String ownerId,
        String matchId) {
}
