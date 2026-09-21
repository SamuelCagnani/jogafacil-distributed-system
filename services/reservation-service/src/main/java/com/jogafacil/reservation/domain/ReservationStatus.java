package com.jogafacil.reservation.domain;

/**
 * Ciclo de vida de uma reserva.
 *
 * <p>Uma reserva {@link #ACTIVE} ocupa o par {@code (courtId, slotId)}; ao ser
 * {@link #CANCELLED} o horario volta a ficar disponivel para uma nova reserva.</p>
 */
public enum ReservationStatus {
    ACTIVE,
    CANCELLED
}
