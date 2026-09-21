package com.jogafacil.reservation.api.dto;

import com.jogafacil.reservation.domain.Reservation;

/**
 * Representacao de uma reserva devolvida pela API (contrato v1).
 *
 * <p>Os instantes sao serializados em ISO-8601 UTC.</p>
 */
public record ReservationResponse(
        String reservationId,
        String courtId,
        String slotId,
        String ownerId,
        String matchId,
        String status,
        String createdAt,
        String cancelledAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.reservationId(),
                reservation.courtId(),
                reservation.slotId(),
                reservation.ownerId(),
                reservation.matchId(),
                reservation.status().name(),
                reservation.createdAt().toString(),
                reservation.cancelledAt() == null ? null : reservation.cancelledAt().toString());
    }
}
