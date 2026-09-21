package com.jogafacil.reservation.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogafacil.contracts.error.ApiException;
import com.jogafacil.contracts.error.ProblemCode;
import com.jogafacil.reservation.api.dto.ReservationCreateRequest;
import com.jogafacil.reservation.api.dto.ReservationResponse;
import com.jogafacil.reservation.persistence.IdempotencyRecord;
import com.jogafacil.reservation.persistence.IdempotencyStore;
import com.jogafacil.reservation.persistence.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testa as regras de idempotencia e o mapeamento de conflito do
 * {@link ReservationService} com repositorio e store simulados.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final String OPERATION_ID = "op-1234567890abcdef";
    private static final String DEFAULT_OWNER = "usr_00000000000000000000000000";

    @Mock
    private ReservationRepository repository;
    @Mock
    private IdempotencyStore idempotencyStore;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestHasher requestHasher = new RequestHasher(objectMapper);

    private ReservationService service;
    private ReservationCreateRequest request;

    @BeforeEach
    void setUp() {
        service = new ReservationService(repository, idempotencyStore, objectMapper,
                requestHasher, DEFAULT_OWNER);
        request = new ReservationCreateRequest(
                "court_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "slot_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                null);
    }

    @Test
    void createsTheReservationAndStoresTheIdempotentOutcome() {
        when(idempotencyStore.find(OPERATION_ID)).thenReturn(Optional.empty());
        when(idempotencyStore.claim(eq(OPERATION_ID), anyString())).thenReturn(true);
        when(repository.saveIfSlotFree(any())).thenReturn(true);

        ReservationResult result = service.create(OPERATION_ID, request);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().courtId()).isEqualTo(request.courtId());
        assertThat(result.body().status()).isEqualTo("ACTIVE");
        verify(idempotencyStore).complete(eq(OPERATION_ID), eq(201), anyString());
    }

    @Test
    void replaysTheStoredOutcomeForTheSameKey() throws Exception {
        ReservationResponse stored = new ReservationResponse(
                "res_01ARZ3NDEKTSV4RRFFQ69G5FAV", request.courtId(), request.slotId(),
                request.ownerId(), null, "ACTIVE", Instant.now().toString(), null);
        String body = objectMapper.writeValueAsString(stored);
        when(idempotencyStore.find(OPERATION_ID)).thenReturn(Optional.of(
                new IdempotencyRecord(OPERATION_ID, requestHasher.hash(request), 201, body)));

        ReservationResult result = service.create(OPERATION_ID, request);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).isEqualTo(stored);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsTheSameKeyReusedWithADifferentBody() {
        when(idempotencyStore.find(OPERATION_ID)).thenReturn(Optional.of(
                new IdempotencyRecord(OPERATION_ID, "another-hash", 201, "{}")));

        ReservationCreateRequest different = new ReservationCreateRequest(
                request.courtId(), "slot_01ARZ3NDEKTSV4RRFFQ69G5FBT", request.ownerId(), null);

        assertThatThrownBy(() -> service.create(OPERATION_ID, different))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ProblemCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void mapsAFailedConditionToConflictingReservation() {
        when(idempotencyStore.find(OPERATION_ID)).thenReturn(Optional.empty());
        when(idempotencyStore.claim(eq(OPERATION_ID), anyString())).thenReturn(true);
        when(repository.saveIfSlotFree(any())).thenReturn(false);

        assertThatThrownBy(() -> service.create(OPERATION_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ProblemCode.RESERVATION_CONFLICT);

        verify(idempotencyStore).release(OPERATION_ID);
        verify(idempotencyStore, never()).complete(anyString(), any(Integer.class), anyString());
    }
}
