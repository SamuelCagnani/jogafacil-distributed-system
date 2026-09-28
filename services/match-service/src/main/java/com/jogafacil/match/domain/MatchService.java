package com.jogafacil.match.domain;

import com.jogafacil.contracts.error.ApiException;
import com.jogafacil.contracts.error.ProblemCode;
import com.jogafacil.contracts.id.UlidGenerator;
import com.jogafacil.match.api.dto.CreateMatchRequest;
import com.jogafacil.match.client.ReservationClient;
import com.jogafacil.match.client.ReservationConflictException;
import com.jogafacil.match.client.ReservationRequest;
import com.jogafacil.match.client.ReservationServiceUnavailableException;
import com.jogafacil.match.client.ReservedSlot;
import com.jogafacil.match.persistence.MatchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orquestra a criacao de partidas.
 *
 * <p>Fluxo: gera o {@code matchId}, chama o reservation-service para segurar o
 * horario e, apenas se a reserva for confirmada, persiste a partida. Um horario
 * ja reservado vira {@code RESERVATION_CONFLICT} (409) e a indisponibilidade do
 * reservation-service vira 503 - a partida nunca e criada sem reserva.</p>
 */
@Service
public class MatchService {

    private final ReservationClient reservationClient;
    private final MatchRepository matchRepository;

    public MatchService(ReservationClient reservationClient, MatchRepository matchRepository) {
        this.reservationClient = reservationClient;
        this.matchRepository = matchRepository;
    }

    public Match create(String idempotencyKey, CreateMatchRequest request) {
        String matchId = UlidGenerator.generate(UlidGenerator.MATCH_PREFIX);
        String operationKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? "match-hold:" + matchId
                : "match-hold:" + idempotencyKey;

        ReservedSlot hold = holdSlot(operationKey, matchId, request);

        Match match = new Match(
                matchId,
                request.courtId(),
                request.slotId(),
                request.organizerId(),
                hold.reservationId(),
                request.maxParticipants(),
                0,
                MatchStatus.OPEN,
                Instant.now());
        matchRepository.save(match);
        return match;
    }

    public Match get(String matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found: " + matchId));
    }

    private ReservedSlot holdSlot(String operationKey, String matchId, CreateMatchRequest request) {
        try {
            return reservationClient.reserve(operationKey, new ReservationRequest(
                    request.courtId(), request.slotId(), request.organizerId(), matchId));
        } catch (ReservationConflictException conflict) {
            throw ApiException.conflict(ProblemCode.RESERVATION_CONFLICT,
                    "Court slot is already reserved: " + request.courtId() + "/" + request.slotId());
        } catch (ReservationServiceUnavailableException unavailable) {
            throw new ApiException(ProblemCode.INTERNAL_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "reservation-service is unavailable, try again later");
        }
    }
}
