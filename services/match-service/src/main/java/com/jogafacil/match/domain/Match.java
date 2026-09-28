package com.jogafacil.match.domain;

import java.time.Instant;

/**
 * Partida criada por um organizador para um horario de quadra.
 *
 * <p>Guarda o {@code reservationId}: a partida so existe se o horario foi
 * previamente reservado no reservation-service, mantendo a reserva como fonte
 * de verdade da ocupacao da quadra.</p>
 */
public record Match(
        String matchId,
        String courtId,
        String slotId,
        String organizerId,
        String reservationId,
        int maxParticipants,
        int participantCount,
        MatchStatus status,
        Instant createdAt) {
}
