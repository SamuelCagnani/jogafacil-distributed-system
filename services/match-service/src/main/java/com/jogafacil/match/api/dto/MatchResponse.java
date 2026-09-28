package com.jogafacil.match.api.dto;

import com.jogafacil.match.domain.Match;

/**
 * Representacao de uma partida devolvida pela API.
 */
public record MatchResponse(
        String matchId,
        String courtId,
        String slotId,
        String organizerId,
        String reservationId,
        int maxParticipants,
        int participantCount,
        String status,
        String createdAt) {

    public static MatchResponse from(Match match) {
        return new MatchResponse(
                match.matchId(),
                match.courtId(),
                match.slotId(),
                match.organizerId(),
                match.reservationId(),
                match.maxParticipants(),
                match.participantCount(),
                match.status().name(),
                match.createdAt().toString());
    }
}
