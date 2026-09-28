package com.jogafacil.match.client;

/**
 * Lancada quando o reservation-service responde HTTP 409: o horario ja possui
 * uma reserva ativa.
 */
public class ReservationConflictException extends RuntimeException {

    public ReservationConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReservationConflictException(String message) {
        super(message);
    }
}
