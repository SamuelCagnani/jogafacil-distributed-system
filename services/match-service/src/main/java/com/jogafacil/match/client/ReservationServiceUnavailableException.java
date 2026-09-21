package com.jogafacil.match.client;

/**
 * Lancada quando o reservation-service esta inacessivel ou responde 5xx,
 * permitindo que o match-service degrade de forma controlada (HTTP 503).
 */
public class ReservationServiceUnavailableException extends RuntimeException {

    public ReservationServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReservationServiceUnavailableException(String message) {
        super(message);
    }
}
