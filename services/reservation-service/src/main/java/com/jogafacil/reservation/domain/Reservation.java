package com.jogafacil.reservation.domain;

import java.time.Instant;

/**
 * Reserva de um horario de quadra.
 *
 * <p>A chave de negocio e o par {@code (courtId, slotId)}: no maximo uma reserva
 * ativa pode existir para esse par. {@code matchId} e opcional e relaciona a
 * reserva a uma partida; {@code ownerId} identifica quem reservou.</p>
 */
public record Reservation(
        String reservationId,
        String courtId,
        String slotId,
        String ownerId,
        String matchId,
        ReservationStatus status,
        Instant createdAt,
        Instant cancelledAt) {

    public Reservation cancelled() {
        return new Reservation(reservationId, courtId, slotId, ownerId, matchId,
                ReservationStatus.CANCELLED, createdAt, Instant.now());
    }
}
