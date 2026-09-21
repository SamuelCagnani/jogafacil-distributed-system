package com.jogafacil.reservation.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogafacil.contracts.error.ApiException;
import com.jogafacil.contracts.error.ProblemCode;
import com.jogafacil.contracts.id.UlidGenerator;
import com.jogafacil.reservation.api.dto.ReservationCreateRequest;
import com.jogafacil.reservation.api.dto.ReservationResponse;
import com.jogafacil.reservation.persistence.IdempotencyRecord;
import com.jogafacil.reservation.persistence.IdempotencyStore;
import com.jogafacil.reservation.persistence.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Regras de negocio das reservas.
 *
 * <p>Fluxo de criacao idempotente:</p>
 * <ol>
 *   <li>se a {@code Idempotency-Key} ja foi concluida, o resultado e reproduzido;</li>
 *   <li>se a mesma chave chegar com corpo diferente, a requisicao e rejeitada
 *       com {@code IDEMPOTENCY_KEY_REUSED} (HTTP 422);</li>
 *   <li>caso contrario a chave e reservada de forma condicional e a reserva e
 *       gravada com escrita condicional; um horario ja ocupado vira
 *       {@code RESERVATION_CONFLICT} (HTTP 409).</li>
 * </ol>
 */
@Service
public class ReservationService {

    private final ReservationRepository repository;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final RequestHasher requestHasher;
    private final String defaultOwnerId;

    public ReservationService(ReservationRepository repository,
                              IdempotencyStore idempotencyStore,
                              ObjectMapper objectMapper,
                              RequestHasher requestHasher,
                              @Value("${jogafacil.default-owner-id}") String defaultOwnerId) {
        this.repository = repository;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.requestHasher = requestHasher;
        this.defaultOwnerId = defaultOwnerId;
    }

    public ReservationResult create(String operationId, ReservationCreateRequest request) {
        String requestHash = requestHasher.hash(request);

        Optional<IdempotencyRecord> existing = idempotencyStore.find(operationId);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        if (!idempotencyStore.claim(operationId, requestHash)) {
            IdempotencyRecord record = idempotencyStore.find(operationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record disappeared for " + operationId));
            return replay(record, requestHash);
        }

        Reservation reservation = new Reservation(
                UlidGenerator.generate(UlidGenerator.RESERVATION_PREFIX),
                request.courtId(),
                request.slotId(),
                request.ownerId() == null ? defaultOwnerId : request.ownerId(),
                request.matchId(),
                ReservationStatus.ACTIVE,
                Instant.now(),
                null);

        if (!repository.saveIfSlotFree(reservation)) {
            idempotencyStore.release(operationId);
            throw ApiException.conflict(ProblemCode.RESERVATION_CONFLICT,
                    "Court slot is no longer available.");
        }

        ReservationResponse body = ReservationResponse.from(reservation);
        idempotencyStore.complete(operationId, HttpStatus.CREATED.value(), write(body));
        return new ReservationResult(HttpStatus.CREATED.value(), body);
    }

    public Reservation get(String reservationId) {
        return repository.findById(reservationId)
                .orElseThrow(() -> ApiException.notFound("Reservation not found: " + reservationId));
    }

    public Reservation cancel(String reservationId) {
        Reservation reservation = get(reservationId);
        if (reservation.status() == ReservationStatus.CANCELLED) {
            return reservation;
        }
        repository.cancel(reservation);
        return reservation.cancelled();
    }

    private ReservationResult replay(IdempotencyRecord record, String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw new ApiException(ProblemCode.IDEMPOTENCY_KEY_REUSED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key was already used with a different request body.");
        }
        if (record.responseBody() == null) {
            throw ApiException.conflict(ProblemCode.RESERVATION_CONFLICT,
                    "The same operation is still being processed.");
        }
        return new ReservationResult(HttpStatus.OK.value(), read(record.responseBody()));
    }

    private String write(ReservationResponse body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialize the reservation response", failure);
        }
    }

    private ReservationResponse read(String body) {
        try {
            return objectMapper.readValue(body, ReservationResponse.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not deserialize the stored response", failure);
        }
    }
}
